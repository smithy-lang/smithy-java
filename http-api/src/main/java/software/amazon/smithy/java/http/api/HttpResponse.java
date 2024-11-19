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
 * HTTP response.
 */
public interface HttpResponse extends HttpMessage {
    /*
     * Get the status code of the response.
     *
     * @return the status code.
     */
    int statusCode();

    /**
     * Get a modifiable version of the response.
     *
     * @return the modifiable response.
     */
    ModifiableHttpResponse toModifiable();

    /**
     * Create a builder configured with the values of the response.
     *
     * @return the created builder.
     */
    default Builder toBuilder() {
        return builder()
            .httpVersion(httpVersion())
            .statusCode(statusCode())
            .headers(headers())
            .body(body());
    }

    /**
     * Create a builder.
     *
     * @return the created builder.
     */
    static Builder builder() {
        return new HttpResponseImpl.Builder();
    }

    /**
     * HTTP response message builder.
     */
    interface Builder {
        /**
         * Create the response.
         *
         * @return the created response.
         * @throws NullPointerException if status code is missing.
         */
        HttpResponse build();

        /**
         * Build a modifiable HTTP response.
         *
         * @return the mutable HTTP response.
         */
        ModifiableHttpResponse buildModifiable();

        /**
         * Set the HTTP version.
         *
         * @param httpVersion HTTP version of the message.
         * @return the builder.
         */
        Builder httpVersion(HttpVersion httpVersion);

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
         * Set the status code of the response.
         *
         * @param statusCode Response status code.
         * @return the builder.
         */
        Builder statusCode(int statusCode);

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
