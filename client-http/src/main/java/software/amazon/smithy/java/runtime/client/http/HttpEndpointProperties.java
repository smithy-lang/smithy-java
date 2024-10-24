/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;

public final class HttpEndpointProperties {

    private HttpEndpointProperties() {}

    /**
     * Custom HTTP headers returned from an {@link EndpointResolver} to use with a request.
     */
    public static final Context.Key<HttpHeaders> HTTP_HEADERS = Context.key(
        "HTTP headers to use with the request"
    );
}
