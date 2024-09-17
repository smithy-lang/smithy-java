/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.client.endpoint.api.Endpoint;
import software.amazon.smithy.java.runtime.common.uri.URIBuilder;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;

/**
 * An abstract class for implementing HTTP-Based protocol.
 */
public abstract class HttpClientProtocol implements ClientProtocol<SmithyHttpRequest, SmithyHttpResponse> {

    private final String id;

    public HttpClientProtocol(String id) {
        this.id = id;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final Class<SmithyHttpRequest> requestClass() {
        return SmithyHttpRequest.class;
    }

    @Override
    public final Class<SmithyHttpResponse> responseClass() {
        return SmithyHttpResponse.class;
    }

    @Override
    public SmithyHttpRequest setServiceEndpoint(SmithyHttpRequest request, Endpoint endpoint) {
        var uri = endpoint.uri();
        URIBuilder builder = URIBuilder.of(request.uri());

        if (uri.getScheme() != null) {
            builder.scheme(uri.getScheme());
        }

        if (uri.getHost() != null) {
            builder.host(uri.getHost());
        }

        if (uri.getPort() > -1) {
            builder.port(uri.getPort());
        }

        // If a path is set on the service endpoint, concatenate it with the path of the request.
        if (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) {
            builder.path(uri.getRawPath());
            builder.concatPath(request.uri().getPath());
        }

        // Merge in any HTTP headers found on the endpoint.
        if (endpoint.property(HttpEndpointProperties.HTTP_HEADERS) != null) {
            request = request.withAddedHeaders(endpoint.property(HttpEndpointProperties.HTTP_HEADERS));
        }

        return request.withUri(builder.build());
    }
}
