/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.util.ArrayList;
import java.util.HashMap;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

public interface SmithyHttpMessage {

    String startLine();

    SmithyHttpVersion httpVersion();

    SmithyHttpMessage withHttpVersion(SmithyHttpVersion version);

    default String contentType() {
        return contentType(null);
    }

    default String contentType(String defaultValue) {
        var result = headers().contentType();
        return result != null ? result : defaultValue;
    }

    default Long contentLength() {
        return headers().contentLength();
    }

    default long contentLength(long defaultValue) {
        var length = contentLength();
        return length == null ? defaultValue : length;
    }

    HttpHeaders headers();

    /**
     * Replaces all headers on the message with the given headers.
     *
     * @param headers Headers to use instead of the existing headers.
     * @return the updated message.
     */
    SmithyHttpMessage withHeaders(HttpHeaders headers);

    /**
     * Add headers to the existing headers of the message.
     *
     * @param headers Headers to add, appending to any existing headers if present.
     * @return the updated message.
     */
    default SmithyHttpMessage withAddedHeaders(HttpHeaders headers) {
        if (headers().isEmpty()) {
            return withHeaders(headers);
        }

        var current = headers.map();
        var updated = new HashMap<>(current);
        for (var entry : headers.map().entrySet()) {
            var field = entry.getKey();
            for (var value : entry.getValue()) {
                updated.computeIfAbsent(field, f -> new ArrayList<>()).add(value);
            }
        }

        return withHeaders(HttpHeaders.of(updated));
    }

    /**
     * Add header fields and values to the current message.
     *
     * <p>If a header field already exists, the header field will have multiple values.
     *
     * @param fieldAndValues An array where even entries are fields, and odd entries are values for the field.
     * @return Returns the created message.
     */
    default SmithyHttpMessage withAddedHeaders(String... fieldAndValues) {
        if (fieldAndValues.length == 0) {
            return this;
        } else if (fieldAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Uneven number of header keys and fields: " + fieldAndValues.length);
        }
        var current = new HashMap<>(headers().map());
        for (int i = 0; i < fieldAndValues.length - 1; i += 2) {
            String field = fieldAndValues[i];
            String value = fieldAndValues[i + 1];
            current.computeIfAbsent(field, f -> new ArrayList<>()).add(value);
        }
        // Note that header implementations themselves handle normalizing header names to lowercase.
        return withHeaders(HttpHeaders.of(current));
    }

    DataStream body();

    SmithyHttpMessage withBody(DataStream stream);
}
