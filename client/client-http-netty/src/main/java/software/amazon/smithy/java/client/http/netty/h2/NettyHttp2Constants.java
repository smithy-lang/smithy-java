/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import io.netty.handler.codec.http2.Http2Connection;
import io.netty.util.AttributeKey;

/**
 * Constant values used in the Netty HTTP/2 client.
 */
final class NettyHttp2Constants {

    // Handler names
    /**
     * Name for the H2 frame codec handler.
     */
    public static final String HTTP2_FRAME_CODEC = "smithy-java.netty.http2-frame-codec";

    /**
     * Name for the H2 multiplexer handler.
     */
    public static final String HTTP2_MULTIPLEX = "smithy-java.netty.http2-multiplexer";

    /**
     * Name fo the H2 settings frame handler.
     */
    public static final String HTTP2_SETTINGS = "smithy-java.netty.http2-settings";

    /**
     * Name fo the H2 settings ack frame handler.
     */
    public static final String HTTP2_ACK_SETTINGS = "smithy-java.netty.http2-ack-settings";

    /**
     * Key identifying the multiplexed connection pool for a parent channel. Parent channels are not directly returned
     * by the APIs but can be retrieved by handlers by accessing the parent channel from an stream channel.
     */
    public static final AttributeKey<Http2MultiplexedConnectionPool> HTTP2_MULTIPLEXED_CONNECTION_POOL =
            AttributeKey.valueOf("smithy-java.netty.http2-multiplexed-connection-pool");

    /**
     * Key identifying the configured initial window size for H2 connections.
     */
    public static final AttributeKey<Integer> HTTP2_INITIAL_WINDOW_SIZE =
            AttributeKey.valueOf("smithy-java.netty.http2-initial-window-size");

    /**
     * Key identifying the attribute that keeps the HTTP2 connection in the parent channel.
     */
    public static final AttributeKey<Http2Connection> HTTP2_CONNECTION =
            AttributeKey.valueOf("smithy-java.netty.http2-connection");

    /**
     * Key to identify the attribute for the multiplexed channel. This attribute is associated to a the parent channel.
     */
    public static final AttributeKey<MultiplexedChannel> HTTP2_MULTIPLEXED_CHANNEL = AttributeKey.valueOf(
            "smithy-java.netty.multiplexed-channel");

    /**
     * Key to identify the configured maximum amount of concurrent streams for a single channel.
     */
    static final AttributeKey<Long> HTTP2_MAX_CONCURRENT_STREAMS =
            AttributeKey.valueOf("smithy-java.netty.max-concurrent-streams");

    private NettyHttp2Constants() {}
}
