/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.uri.URIBuilder;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * An abstract class for implementing HTTP-Based protocol.
 */
public abstract class HttpClientProtocol implements ClientProtocol<HttpRequest, HttpResponse> {

    private final ShapeId id;

    public HttpClientProtocol(ShapeId id) {
        this.id = id;
    }

    @Override
    public final ShapeId id() {
        return id;
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpRequest setServiceEndpoint(HttpRequest request, Endpoint endpoint) {
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

        var requestBuilder = request.toBuilder();

        // Merge in any HTTP headers found on the endpoint.
        if (endpoint.property(HttpContext.ENDPOINT_RESOLVER_HTTP_HEADERS) != null) {
            requestBuilder.withAddedHeaders(endpoint.property(HttpContext.ENDPOINT_RESOLVER_HTTP_HEADERS));
        }

        return requestBuilder.uri(builder.build()).build();
    }
}
