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

import io.moquette.interception.messages.InterceptAcknowledgedMessage;
import io.moquette.broker.subscriptions.Subscription;
import io.moquette.interception.messages.InterceptExceptionMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;

/**
 * This interface is to be used internally by the broker components.
 * <p>
 * An interface is used instead of a class to allow more flexibility in changing an implementation.
 * <p>
 * Interceptor implementations forward notifications to a <code>InterceptHandler</code>, that is
 * normally a field. So, the implementations should act as a proxy to a custom intercept handler.
 *
 * @see InterceptHandler
 */
public interface Interceptor {

    void notifyClientConnected(MqttConnectMessage msg);

    void notifyClientDisconnected(String clientID, String username);

    void notifyClientConnectionLost(String clientID, String username);

    void notifyClientPing(String ClientID);

    void notifyTopicPublished(MqttPublishMessage msg, String clientID, String username);

    void notifyTopicSubscribed(Subscription sub, String username);

    void notifyTopicUnsubscribed(String topic, String clientID, String username);

    void notifyMessageAcknowledged(InterceptAcknowledgedMessage msg);

    void notifyLoopException(InterceptExceptionMessage th);

    void addInterceptHandler(InterceptHandler interceptHandler);

    void removeInterceptHandler(InterceptHandler interceptHandler);
}
