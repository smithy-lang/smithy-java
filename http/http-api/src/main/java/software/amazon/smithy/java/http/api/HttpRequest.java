/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.net.URI;

/**
 * HTTP request.
 */
public interface HttpRequest extends HttpMessage {
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
    ModifiableHttpRequest toModifiable();

    /**
     * Creates an unmodifiable copy of the request, or returns it as is if it is already unmodifiable.
     *
     * @return the unmodifiable version of this request.
     */
    HttpRequest toUnmodifiable();

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
        return new HttpRequestImpl.Builder();
    }

    /**
     * HTTP request message builder.
     */
    interface Builder extends HttpMessage.Builder<Builder> {
        /**
         * Create the request.
         *
         * @return the created request.
         * @throws NullPointerException if method or uri are missing.
         */
        HttpRequest build();

        /**
         * Build a modifiable HTTP request.
         *
         * @return the mutable HTTP request.
         */
        ModifiableHttpRequest buildModifiable();

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
    }
}
