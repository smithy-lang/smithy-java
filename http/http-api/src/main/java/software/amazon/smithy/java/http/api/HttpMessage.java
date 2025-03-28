/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.io.datastream.DataStream;

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

    /**
     * Builder for HTTP messages.
     *
     * @param <B> Builder type.
     */
    @SuppressWarnings("rawtypes")
    interface Builder<B extends Builder> {
        /**
         * Set the HTTP version.
         *
         * @param httpVersion HTTP version of the message.
         * @return the builder.
         */
        B httpVersion(HttpVersion httpVersion);

        /**
         * Set the body of the message.
         *
         * @param publisher Body to set.
         * @return the builder.
         */
        default B body(Flow.Publisher<ByteBuffer> publisher) {
            return body(DataStream.ofPublisher(publisher, null, -1));
        }

        /**
         * Set the body of the message.
         *
         * @param body Body to set.
         * @return the builder.
         */
        B body(DataStream body);

        /**
         * Set the headers of the message, replacing any previously set headers.
         *
         * @param headers Headers to set.
         * @return the builder.
         */
        B headers(HttpHeaders headers);

        /**
         * Add and merge in the given headers to the message.
         *
         * @param name  Header name to append.
         * @param value Header value to append.
         * @return the builder.
         */
        B withAddedHeader(String name, String value);

        /**
         * Add and merge in the given headers to the message.
         *
         * @param headers Headers to add and merge in.
         * @return the builder.
         */
        B withAddedHeaders(Map<String, List<String>> headers);

        /**
         * Add and merge in the given headers to the message.
         *
         * @param headers Headers to add and merge in.
         * @return the builder.
         */
        default B withAddedHeaders(HttpHeaders headers) {
            return withAddedHeaders(headers.map());
        }

        /**
         * Put the given headers onto the message, replacing any existing headers with the same names.
         *
         * @param headers Headers to put.
         * @return the builder.
         */
        B withReplacedHeaders(Map<String, List<String>> headers);

        /**
         * Put the given headers onto the message, replacing any existing headers with the same names.
         *
         * @param headers Headers to put.
         * @return the builder.
         */
        default B withReplacedHeaders(HttpHeaders headers) {
            return withReplacedHeaders(headers.map());
        }

        /**
         * Replaces a header.
         *
         * @param name  Header name to replace.
         * @param values Header value to replace.
         * @return the builder.
         */
        default B withReplacedHeader(String name, List<String> values) {
            return withReplacedHeaders(Map.of(name, values));
        }
    }
}
