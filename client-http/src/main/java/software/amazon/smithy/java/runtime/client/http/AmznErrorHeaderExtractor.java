/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeIdSyntaxException;

/**
 * Attempts to extract the Amazon-specific X-Amzn-Errortype error header and map it to a builder in a type registry.
 */
public final class AmznErrorHeaderExtractor implements HttpErrorDeserializer.HeaderErrorExtractor {

    private static final String ERROR_HEADER = "x-amzn-errortype";

    @Override
    public boolean hasHeader(SmithyHttpResponse response) {
        return response.headers().firstValue(ERROR_HEADER).isPresent();
    }

    @Override
    public ShapeId resolveId(SmithyHttpResponse response, String serviceNamespace, TypeRegistry registry) {
        var header = response.headers().firstValue(ERROR_HEADER).orElse(null);
        return header == null ? null : toShapeId(header, serviceNamespace, registry);
    }

    private static ShapeId toShapeId(String error, String serviceNamespace, TypeRegistry registry) {
        error = sanitizeErrorId(error);
        var hashPos = error.indexOf('#');

        // Try to find a shape in the registry that exactly matches the shape ID if it's absolute.
        if (hashPos > 0) {
            try {
                var id1 = ShapeId.from(error);
                if (registry.getShapeClass(id1) != null) {
                    return id1;
                }
            } catch (ShapeIdSyntaxException ignored) {}

            // Not found, so strip off the absolute namespace and try with the service.
            error = error.substring(hashPos + 1);
        }

        // Try to find an error class that matches the relative ID + the service namespace.
        try {
            var id2 = ShapeId.fromOptionalNamespace(serviceNamespace, error);
            if (registry.getShapeClass(id2) != null) {
                return id2;
            }
        } catch (ShapeIdSyntaxException ignored) {}

        return null;
    }

    private static String sanitizeErrorId(String errorId) {
        var colon = errorId.indexOf(':');
        if (colon > 0) {
            errorId = errorId.substring(0, colon);
        }
        return errorId;
    }
}
