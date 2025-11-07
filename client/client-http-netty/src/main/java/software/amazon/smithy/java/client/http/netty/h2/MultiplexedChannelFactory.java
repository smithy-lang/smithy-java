/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import io.netty.channel.Channel;

/**
 * Creates {@link MultiplexedChannel} instances.
 * <p>
 * This interface exists to allow testing the initialization pipeline.
 */
@FunctionalInterface
interface MultiplexedChannelFactory {

    /**
     * Creates a new multiplexed channel.
     *
     * @param parentChannel        The parent channel
     * @param maxConcurrentStreams The configured max concurrent streams
     * @param streamBootstrap      The stream bootstrap
     * @return A new multiplexed channel instance.
     */
    MultiplexedChannel createChannel(
            Channel parentChannel,
            int maxConcurrentStreams,
            Http2StreamBootstrap streamBootstrap
    );
}
