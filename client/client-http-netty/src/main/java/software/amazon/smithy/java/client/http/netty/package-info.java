/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Client HTTP transport backed by Netty.
 *
 * <p>Supports HTTP/1.1, HTTP/2 (over TLS with ALPN), and HTTP/2 cleartext (h2c prior knowledge).
 * Provides the same {@code ClientTransport<HttpRequest, HttpResponse>} interface as other
 * Smithy-Java HTTP transports, with blocking APIs that stream request and response bodies.
 *
 * @see software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport
 */
package software.amazon.smithy.java.client.http.netty;
