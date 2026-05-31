/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import software.amazon.smithy.java.http.api.HttpRequestFactory;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;

/**
 * {@link HttpRequestFactory} that backs request headers with a Netty header container, so an HTTP
 * protocol serializes a request directly into the transport's native representation. The same
 * container is then reused by reference on the send path (see {@link NettyUtils#fillH1Headers}),
 * eliminating the smithy&rarr;Netty header marshalling copy.
 *
 * <p>Stateless and safe to share across requests; each call returns a fresh header set.
 */
final class NettyHttpRequestFactory implements HttpRequestFactory {

    static final NettyHttpRequestFactory INSTANCE = new NettyHttpRequestFactory();

    private NettyHttpRequestFactory() {}

    @Override
    public ModifiableHttpHeaders newRequestHeaders(int expectedPairs) {
        return new NettyModifiableH1Headers();
    }
}
