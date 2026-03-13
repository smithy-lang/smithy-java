/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.Schema;

/**
 * Writes the error type discriminator header for an error response.
 *
 * <p>This is the server-side counterpart to the client-side
 * {@code HttpErrorDeserializer.HeaderErrorExtractor}. Different protocols use different
 * mechanisms to communicate the error type to clients. For example, AWS protocols use the
 * {@code X-Amzn-Errortype} header, while other protocols may use a different header or
 * not write one at all.
 */
@FunctionalInterface
public interface HeaderErrorSerializer {
    /**
     * Default implementation that writes the {@code X-Amzn-Errortype} header using the
     * full shape ID of the error.
     */
    HeaderErrorSerializer AMZN_ERROR_HEADER = (schema, headers, context) -> {
        headers.put("X-Amzn-Errortype", List.of(schema.id().toString()));
    };

    /**
     * No-op implementation that does not write any error type discriminator header.
     */
    HeaderErrorSerializer NONE = (schema, headers, context) -> {};

    /**
     * Write the error type discriminator for the given error schema.
     *
     * @param errorSchema Schema of the error being serialized.
     * @param headers     Mutable header map to write discriminator headers to.
     * @param context     Context for the current request, which may contain protocol-specific
     *                    information such as the service namespace.
     */
    void writeErrorType(Schema errorSchema, Map<String, List<String>> headers, Context context);
}
