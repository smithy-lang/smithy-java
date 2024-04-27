/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.client.endpoints.api.Endpoint;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.uri.URIBuilder;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;

/**
 * An abstract class for implementing HTTP-Based protocol.
 */
public abstract class HttpClientProtocol implements ClientProtocol<SmithyHttpRequest, SmithyHttpResponse> {

    private static final System.Logger LOGGER = System.getLogger(HttpClientProtocol.class.getName());
    private static final Set<String> TEXT_CONTENT_TYPES = Set.of("application/json", "application/xml");

    private final String id;

    public HttpClientProtocol(String id) {
        this.id = id;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final Context.Key<SmithyHttpRequest> requestKey() {
        return HttpContext.HTTP_REQUEST;
    }

    @Override
    public final Context.Key<SmithyHttpResponse> responseKey() {
        return HttpContext.HTTP_RESPONSE;
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
        if (endpoint.endpointAttribute(HttpEndpointKeys.HTTP_HEADERS) != null) {
            request = request.withAddedHeaders(endpoint.endpointAttribute(HttpEndpointKeys.HTTP_HEADERS));
        }

        return request.withUri(builder.build());
    }

    /**
     * Create an exception for an unmodeled error using HTTP protocol hints.
     *
     * @param operationId Operation that encountered the error.
     * @param response    HTTP response that was received.
     * @return the created exception.
     */
    public static SdkException createErrorFromHints(String operationId, SmithyHttpResponse response) {
        SdkException.Fault fault = SdkException.Fault.ofHttpStatusCode(response.statusCode());
        StringBuilder message = new StringBuilder();
        message.append(switch (fault) {
            case CLIENT -> "Client error ";
            case SERVER -> "Server error ";
            default -> "Unknown error ";
        });

        message.append("encountered from operation ").append(operationId);
        message.append(System.lineSeparator());
        message.append(response.httpVersion()).append(' ').append(response.statusCode()).append(System.lineSeparator());

        response.headers().map().forEach((field, values) -> {
            values.forEach(value -> {
                message.append(field).append(": ").append(value).append(System.lineSeparator());
            });
        });

        message.append(System.lineSeparator());

        String contentType = response.headers()
            .firstValue("Content-Type")
            .orElse("application/octet-stream")
            .toLowerCase(Locale.ENGLISH);

        if (!isText(contentType)) {
            return new SdkException(message.toString(), fault);
        }

        try {
            message.append(new String(response.body().readNBytes(16384), StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.TRACE, "Unable to append exception response body for %s", operationId);
        }

        return new SdkException(message.toString(), fault);
    }

    private static boolean isText(String contentType) {
        return contentType.startsWith("text/") || contentType.contains("charset=utf-8") || contentType.endsWith("+json")
            || contentType.endsWith("+xml") || TEXT_CONTENT_TYPES.contains(contentType);
    }
}
