/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.moquette.interception;

import io.moquette.BrokerConstants;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.broker.subscriptions.Subscription;
import io.moquette.broker.subscriptions.Topic;
import io.moquette.interception.messages.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class BrokerInterceptorTest {

    // value to check for changes after every notification
    private static final AtomicInteger n = new AtomicInteger(0);

    // Interceptor loaded with a custom InterceptHandler special for the tests
    private static final class MockObserver implements InterceptHandler {

        @Override
        public String getID() {
            return "MockObserver";
        }

        @Override
        public Class<?>[] getInterceptedMessageTypes() {
            return InterceptHandler.ALL_MESSAGE_TYPES;
        }

        @Override
        public void onConnect(InterceptConnectMessage msg) {
            n.set(40);
        }

        @Override
        public void onDisconnect(InterceptDisconnectMessage msg) {
            n.set(50);
        }

        @Override
        public void onConnectionLost(InterceptConnectionLostMessage msg) {
            n.set(30);
        }

        @Override
        public void onPingRequest(InterceptPingRequestMessage msg) {
            n.set(100);
        }

        @Override
        public void onPublish(InterceptPublishMessage msg) {
            n.set(60);
            msg.getPayload().release();
        }

        @Override
        public void onSubscribe(InterceptSubscribeMessage msg) {
            n.set(70);
        }

        @Override
        public void onUnsubscribe(InterceptUnsubscribeMessage msg) {
            n.set(80);
        }

        @Override
        public void onMessageAcknowledged(InterceptAcknowledgedMessage msg) {
            n.set(90);
        }

        @Override
        public void onSessionLoopError(Throwable error) {
            throw new RuntimeException(error);
        }
    }

    private static final BrokerInterceptor interceptor = new BrokerInterceptor(
        Collections.<InterceptHandler>singletonList(new MockObserver()));

    @BeforeAll
    public static void beforeAllTests() {
        // check if any of the handler methods was called before notifications
        assertEquals(0, n.get());
    }

    @AfterAll
    public static void afterAllTests() {
        interceptor.stop();
    }

    /* Used to wait handler notification by the interceptor internal thread */
    private static void interval() throws InterruptedException {
        Thread.sleep(100);
    }

    @Test
    public void testNotifyClientConnected() throws Exception {
        interceptor.notifyClientConnected(MqttMessageBuilders.connect().build());
        interval();
        assertEquals(40, n.get());
    }

    @Test
    public void testNotifyClientDisconnected() throws Exception {
        interceptor.notifyClientDisconnected("cli1234", "cli1234");
        interval();
        assertEquals(50, n.get());
    }

    @Test
    public void testNotifyTopicPublished() throws Exception {
        final ByteBuf payload = Unpooled.copiedBuffer("Hello".getBytes(UTF_8));
        // Internal function call, will not release buffers.
        interceptor.notifyTopicPublished(
                MqttMessageBuilders.publish().qos(MqttQoS.AT_MOST_ONCE)
                    .payload(payload).build(),
                "cli1234",
                "cli1234");
        interval();
        assertEquals(60, n.get());
        payload.release();
    }

    @Test
    public void testNotifyTopicSubscribed() throws Exception {
        interceptor.notifyTopicSubscribed(new Subscription("cli1", new Topic("o2"), MqttQoS.AT_MOST_ONCE), "cli1234");
        interval();
        assertEquals(70, n.get());
    }

    @Test
    public void testNotifyTopicUnsubscribed() throws Exception {
        interceptor.notifyTopicUnsubscribed("o2", "cli1234", "cli1234");
        interval();
        assertEquals(80, n.get());
    }

    @Test
    public void testNotifyPingReq() throws Exception {
        interceptor.notifyClientPing("cli1234");
        interval();
        assertNotSame(100, n.get());

        // Now repeat the test with a new interceptor configured to not acknowledge PINGREQ
        IConfig props = new MemoryConfig(new Properties());
        props.setProperty(BrokerConstants.INTERCEPT_PINGREQ_PROPERTY_NAME, "false");
        BrokerInterceptor pingInterceptor = new BrokerInterceptor(props, Collections.<InterceptHandler>singletonList(new MockObserver()));
        pingInterceptor.notifyClientPing("cli1234");
        interval();
        assertNotSame(100, n.get());

        // Finally with a new interceptor configured to acknowledge PINGREQ
        props = new MemoryConfig(new Properties());
        props.setProperty(BrokerConstants.INTERCEPT_PINGREQ_PROPERTY_NAME, "true");
        pingInterceptor = new BrokerInterceptor(props, Collections.<InterceptHandler>singletonList(new MockObserver()));
        pingInterceptor.notifyClientPing("cli1234");
        interval();
        assertEquals(100, n.get());
    }

    @Test
    public void testAddAndRemoveInterceptHandler() throws Exception {
        InterceptHandler interceptHandlerMock1 = mock(InterceptHandler.class);
        InterceptHandler interceptHandlerMock2 = mock(InterceptHandler.class);
        // add
        interceptor.addInterceptHandler(interceptHandlerMock1);
        interceptor.addInterceptHandler(interceptHandlerMock2);

        Subscription subscription = new Subscription("cli1", new Topic("o2"), MqttQoS.AT_MOST_ONCE);
        interceptor.notifyTopicSubscribed(subscription, "cli1234");
        interval();

        verify(interceptHandlerMock1).onSubscribe(refEq(new InterceptSubscribeMessage(subscription, "cli1234")));
        verify(interceptHandlerMock2).onSubscribe(refEq(new InterceptSubscribeMessage(subscription, "cli1234")));

        // remove
        interceptor.removeInterceptHandler(interceptHandlerMock1);

        interceptor.notifyTopicSubscribed(subscription, "cli1235");
        interval();
        // removeInterceptHandler() performs another interaction
        // TODO: fix this
        // verifyNoMoreInteractions(interceptHandlerMock1);
        verify(interceptHandlerMock2).onSubscribe(refEq(new InterceptSubscribeMessage(subscription, "cli1235")));
    }
}
