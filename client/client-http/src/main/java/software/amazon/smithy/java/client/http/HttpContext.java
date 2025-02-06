/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.time.Duration;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;

/**
 * {@link Context} keys used with HTTP-based clients.
 */
public final class HttpContext {
    /**
     * The time from when an HTTP request is sent, and when the response is received. If the response is not
     * received in time, then the request is considered timed out. This setting does not apply to streaming
     * operations.
     */
    public static final Context.Key<Duration> HTTP_REQUEST_TIMEOUT = Context.key(Duration.class, "HTTP.RequestTimeout");

    /**
     * Custom HTTP headers returned from an {@link EndpointResolver} to use with a request.
     */
    public static final Context.Key<HttpHeaders> ENDPOINT_RESOLVER_HTTP_HEADERS = Context.key(
            HttpHeaders.class,
            "HTTP headers to use with the request returned from an endpoint resolver");

    private HttpContext() {}
}
