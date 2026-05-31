/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.time.Duration;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequestFactory;

/**
 * {@link Context} keys used with HTTP-based clients.
 */
public final class HttpContext {
    /**
     * The time from when an HTTP request is sent, and when the response is received. If the response is not
     * received in time, then the request is considered timed out. This setting does not apply to streaming
     * operations.
     */
    public static final Context.Key<Duration> HTTP_REQUEST_TIMEOUT = Context.key("HTTP.RequestTimeout");

    /**
     * Custom HTTP headers returned from an {@link EndpointResolver} to use with a request.
     */
    public static final Context.Key<HttpHeaders> ENDPOINT_RESOLVER_HTTP_HEADERS = Context.key(
            "HTTP headers to use with the request returned from an endpoint resolver");

    /**
     * The minimum length of bytes threshold for a request body to be compressed. Defaults to 10240 bytes if not set.
     */
    public static final Context.Key<Integer> REQUEST_MIN_COMPRESSION_SIZE_BYTES =
            Context.key("Minimum bytes size for request compression");

    /**
     * If request compression is disabled.
     */
    public static final Context.Key<Boolean> DISABLE_REQUEST_COMPRESSION =
            Context.key("If request compression is disabled");

    /**
     * A transport-supplied factory for the request's mutable containers (headers, and in future the
     * body), letting an HTTP protocol serialize the request directly into the transport's native
     * representation. Published by a transport via {@code ClientTransport.contributeRequestFactory}
     * and read by the protocol's {@code createRequest}. Absent for transports that do not opt in.
     */
    public static final Context.Key<HttpRequestFactory> TRANSPORT_REQUEST_FACTORY =
            Context.key("Transport-supplied HTTP request factory");

    private HttpContext() {}
}
