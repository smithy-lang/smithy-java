/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.Channel;

public final class NettyUtils {
    private static final NettyLogger LOGGER = NettyLogger.getLogger(NettyUtils.class);

    private NettyUtils() {}

    public static class Asserts {
        /**
         * This method is called at points where the control flow should no reached. This will log an ERROR
         * and assert false such that while testing an assertion exception will be thrown.
         */
        public static void shouldNotBeReached(Channel channel, String message) {
            try {
                // We throw here to capture and log the stack trace to know
                // where this call was originated.
                throw new RuntimeException(message);
            } catch (RuntimeException e) {
                LOGGER.error(channel, message, e);
            }

            // This will throw whenever assertions are enabled for the JVM.
            assert false;
        }
    }
}
