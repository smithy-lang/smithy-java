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

    private HttpContext() {}
}
