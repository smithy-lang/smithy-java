/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * HTTP message.
 *
 * <p>When closed, the body of the message is also closed.
 */
public interface HttpMessage extends AutoCloseable {
    /**
     * Get the HTTP version.
     *
     * @return version.
     */
    HttpVersion httpVersion();

    /**
     * Get the content-type header value.
     *
     * @return content-type or null.
     */
    default String contentType() {
        return contentType(null);
    }

    /**
     * Get the content-type header or a default value.
     *
     * @param defaultValue Default value to return if missing.
     * @return the content-type.
     */
    default String contentType(String defaultValue) {
        var result = headers().contentType();
        return result != null ? result : defaultValue;
    }

    /**
     * Get the content-length header value or null.
     *
     * @return the content-length header value as a Long or null.
     */
    default Long contentLength() {
        return headers().contentLength();
    }

    /**
     * Get the content-length or a default value when missing.
     *
     * @param defaultValue Default value to return when missing.
     * @return the content-length or default value.
     */
    default long contentLength(long defaultValue) {
        var length = contentLength();
        return length == null ? defaultValue : length;
    }

    /**
     * Get the headers of the message.
     *
     * @return headers.
     */
    HttpHeaders headers();

    /**
     * Get the body of the message.
     *
     * @return the message body (never null, may be zero length).
     */
    DataStream body();

    @Override
    default void close() {
        var body = body();
        if (body != null) {
            body.close();
        }
    }
}
