/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import software.amazon.smithy.java.runtime.io.datastream.DataStream;

/**
 * HTTP message.
 */
public interface HttpMessage {
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
     * Get the body of the message, or null.
     *
     * @return the message body or null.
     */
    DataStream body();
}
