/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it.server;

import io.netty.channel.Channel;
import software.amazon.smithy.java.client.http.netty.NettyLogger;
import software.amazon.smithy.java.logging.InternalLogger;

public class NettyTestLogger extends NettyLogger {

    protected NettyTestLogger(InternalLogger logger) {
        super(logger);
    }

    /**
     * Creates a new Netty logger.
     *
     * @param clazz The class that creates the logs
     * @return The Netty logger
     */
    public static NettyTestLogger getLogger(Class<?> clazz) {
        return new NettyTestLogger(InternalLogger.getLogger(clazz));
    }

    @Override
    protected String addChannelIdToMessage(String message, Channel channel) {
        return "TEST " + super.addChannelIdToMessage(message, channel);
    }
}
