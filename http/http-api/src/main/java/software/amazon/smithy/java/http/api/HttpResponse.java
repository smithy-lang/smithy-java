/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

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
    interface Builder extends HttpMessage.Builder<HttpResponse.Builder> {
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
         * Set the status code of the response.
         *
         * @param statusCode Response status code.
         * @return the builder.
         */
        Builder statusCode(int statusCode);
    }
}
