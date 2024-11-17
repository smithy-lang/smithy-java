/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

/**
 * HTTP request.
 */
public interface SmithyHttpRequest extends SmithyHttpMessage {
    /**
     * Get the method of the request.
     *
     * @return the method.
     */
    String method();

    /**
     * Get the URI of the request.
     *
     * @return the request URI.
     */
    URI uri();

    /**
     * Get a modifiable version of the request.
     *
     * @return the modifiable request.
     */
    SmithyModifiableHttpRequest toModifiable();

    /**
     * Create a builder configured with the values of the request.
     *
     * @return the created builder.
     */
    default Builder toBuilder() {
        return builder()
            .method(method())
            .uri(uri())
            .headers(headers())
            .body(body())
            .httpVersion(httpVersion());
    }

    /**
     * Create a builder.
     *
     * @return the created builder.
     */
    static Builder builder() {
        return new SmithyHttpRequestImpl.Builder();
    }

    /**
     * HTTP request message builder.
     */
    interface Builder {
        /**
         * Create the request.
         *
         * @return the created request.
         * @throws NullPointerException if method or uri are missing.
         */
        SmithyHttpRequest build();

        /**
         * Build a modifiable HTTP request.
         *
         * @return the mutable HTTP request.
         */
        SmithyModifiableHttpRequest buildModifiable();

        /**
         * Set the HTTP method.
         *
         * @param method Method to set.
         * @return the builder.
         */
        Builder method(String method);

        /**
         * Set the URI of the message.
         *
         * @param uri URI to set.
         * @return the builder.
         */
        Builder uri(URI uri);

        /**
         * Set the HTTP version.
         *
         * @param httpVersion HTTP version of the message.
         * @return the builder.
         */
        Builder httpVersion(SmithyHttpVersion httpVersion);

        /**
         * Set the body of the message.
         *
         * @param publisher Body to set.
         * @return the builder.
         */
        default Builder body(Flow.Publisher<ByteBuffer> publisher) {
            return body(DataStream.ofPublisher(publisher, null, -1));
        }

        /**
         * Set the body of the message.
         *
         * @param body Body to set.
         * @return the builder.
         */
        Builder body(DataStream body);

        /**
         * Set the headers of the message, replacing any previously set headers.
         *
         * @param headers Headers to set.
         * @return the builder.
         */
        Builder headers(HttpHeaders headers);

        /**
         * Add and merge in the given headers to the message.
         *
         * @param headers Headers to add and merge in.
         * @return the builder.
         */
        Builder withAddedHeaders(String... headers);

        /**
         * Add and merge in the given headers to the message.
         *
         * @param headers Headers to add and merge in.
         * @return the builder.
         */
        Builder withAddedHeaders(HttpHeaders headers);

        /**
         * Put the given headers onto the message, replacing any existing headers with the same names.
         *
         * @param headers Headers to put.
         * @return the builder.
         */
        Builder withReplacedHeaders(Map<String, List<String>> headers);
    }
}
