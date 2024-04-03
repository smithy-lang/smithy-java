/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.net.http.HttpHeaders;
import software.amazon.smithy.java.runtime.api.EndpointKey;
import software.amazon.smithy.java.runtime.api.EndpointProvider;

public final class HttpEndpointKeys {

    private HttpEndpointKeys() {}

    /**
     * Custom HTTP headers returned from an {@link EndpointProvider} to use with a request.
     */
    public static final EndpointKey<HttpHeaders> HTTP_HEADERS = EndpointKey.of("HTTP headers to use with the request");
}
