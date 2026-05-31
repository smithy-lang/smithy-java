/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpRequestFactory;
import software.amazon.smithy.java.http.api.HttpResponse;
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

    /**
     * The transport-supplied request factory for this call, if any.
     *
     * <p>HTTP protocols use this to serialize a request directly into the transport's native
     * representation (e.g. headers backed by the transport's own container) instead of a generic one
     * the transport then copies. Returns null when no transport opted in, in which case the default
     * array-backed containers are used.
     *
     * @param context the per-call context.
     * @return the transport request factory, or null.
     */
    protected static HttpRequestFactory requestFactory(Context context) {
        return context == null ? null : context.get(HttpContext.TRANSPORT_REQUEST_FACTORY);
    }

    @Override
    public HttpRequest setServiceEndpoint(HttpRequest request, Endpoint endpoint) {
        var merged = request.uri().withEndpoint(endpoint.uri());
        var modifiableRequest = request.toModifiable();
        modifiableRequest.setUri(merged);

        // Merge in any HTTP headers found on the endpoint.
        if (endpoint.property(HttpContext.ENDPOINT_RESOLVER_HTTP_HEADERS) != null) {
            modifiableRequest.headers().addHeaders(endpoint.property(HttpContext.ENDPOINT_RESOLVER_HTTP_HEADERS));
        }

        return modifiableRequest;
    }
}
