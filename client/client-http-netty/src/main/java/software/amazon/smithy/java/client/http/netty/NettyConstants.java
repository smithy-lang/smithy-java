/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * Constant values used in the Netty HTTP client.
 */
public final class NettyConstants {

    /**
     * Name for the SSL handler.
     */
    public static final String SSL = "smithy.netty.ssl";

    /**
     * Name for the SSL handshake handler.
     */
    public static final String SSL_HANDSHAKE = "smithy.netty.ssl-handshake";

    /**
     * Name for the SSL handler for close completion.
     */
    public static final String SSL_CLOSE_COMPLETE = "smithy.netty.ssl-close-complete";

    /**
     * Name for the HTTP/1.1 codec.
     */
    public static final String HTTP11_CODEC = "smithy.netty.http1_1";

    /**
     * Name for the Application Layer Protocol Negotiation (ALPN) handler.
     */
    public static final String ALPN = "smithy.netty.protocol-negotiation";

    /**
     * Key to identify the future for the HTTP version for the channel. Request and response handlers
     * must wait on this future to complete before starting writing requests or reading responses.
     * A successful completion of this future signals that the channel is ready. A failed completion
     * signals an error and that the channel cannot be used.
     *
     * This future completion can happen in different stages an places depending on the configured HTTP
     * version, and, whether the request is using HTTPS.
     */
    public static final AttributeKey<CompletableFuture<HttpVersion>> HTTP_VERSION_FUTURE =
            AttributeKey.valueOf("smithy.netty.http-version");

    /**
     * Key to identify the channel pool that for the channel. This can be used when closing it to
     * properly release it to its corresponding pool.
     */
    public static final AttributeKey<ChannelPool> CHANNEL_POOL =
            AttributeKey.valueOf("smithy.netty.connection-pool");

    private NettyConstants() {}
}
