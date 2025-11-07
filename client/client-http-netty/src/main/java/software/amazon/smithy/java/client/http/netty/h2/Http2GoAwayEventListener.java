/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MULTIPLEXED_CHANNEL;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MULTIPLEXED_CONNECTION_POOL;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http2.Http2ConnectionAdapter;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.client.http.netty.NettyLogger;

/**
 * Handles GOAWAY frames. GOAWAY is a <em>connection shutdown notice</em> that tells the other peer to stop
 * sending new requests.
 * <p>
 * For currently existing streams
 * <ul>
 *   <li>Streams ≤ lastStreamId → Will be processed, wait for completion</li>
 *   <li>>Streams > lastStreamId → Were NOT processed, safe to retry elsewhere</li>
 * </ul>
 */
final class Http2GoAwayEventListener extends Http2ConnectionAdapter {
    private static final NettyLogger LOGGER = NettyLogger.getLogger(Http2GoAwayEventListener.class);
    private final Channel parentChannel;

    Http2GoAwayEventListener(Channel parentChannel) {
        this.parentChannel = parentChannel;
    }

    @Override
    public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
        var exception = new GoAwayException(errorCode, debugData.toString(StandardCharsets.UTF_8));
        LOGGER.info(parentChannel, "Received GOAWAY, lastStreamId {}", lastStreamId);
        var multiplexedChannel = parentChannel.attr(HTTP2_MULTIPLEXED_CHANNEL).get();
        if (multiplexedChannel != null) {
            multiplexedChannel.handleGoAway(lastStreamId, exception);
            return;
        }
        // The channel is not fully setup yet, just close it.
        var channelPool = parentChannel.attr(HTTP2_MULTIPLEXED_CONNECTION_POOL).get();
        if (channelPool != null) {
            channelPool.closeAndReleaseParent(parentChannel, exception);
            return;
        }
        LOGGER.error(parentChannel,
                "GOAWAY received on a connection not associated with any "
                        + "multiplexed channel pool.");
        parentChannel.pipeline().fireExceptionCaught(exception);
    }
}
