/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import io.netty.channel.Channel;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.util.concurrent.Future;

/**
 * Represents the logic to bootstrap and open an H2 stream from a H2 initialized channel. This is
 * implemented by default as
 * <p>
 * {@snippet
         *return new Http2StreamChannelBootstrap(channel).open()
 *}
 * <p>
 * But encapsulating this logic behind an interface is helpful for testing this logic without the
 * need to have an actual HTTP/2 connection open.
 */
@FunctionalInterface
interface Http2StreamBootstrap {

    /**
     * Bootstraps and opens a new stream channel from the given parent channel.
     *
     * @param parentChannel The parent channel to open the stream from.
     * @return A future that will be notified of the result of the operation
     */
    Future<Http2StreamChannel> openStream(Channel parentChannel);

}
