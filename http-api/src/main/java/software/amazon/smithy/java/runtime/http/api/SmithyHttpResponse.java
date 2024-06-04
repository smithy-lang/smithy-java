/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.net.http.HttpHeaders;

public interface SmithyHttpResponse extends SmithyHttpMessage, AutoCloseable {

    @Override
    default String startLine() {
        return httpVersion() + " " + statusCode();
    }

    int statusCode();

    SmithyHttpResponse withStatusCode(int statusCode);

    @Override
    SmithyHttpResponse withHttpVersion(SmithyHttpVersion version);

    @Override
    SmithyHttpResponse withHeaders(HttpHeaders headers);

    @Override
    default SmithyHttpResponse withAddedHeaders(HttpHeaders headers) {
        return (SmithyHttpResponse) SmithyHttpMessage.super.withAddedHeaders(headers);
    }

    @Override
    default SmithyHttpResponse withAddedHeaders(String... fieldAndValues) {
        return (SmithyHttpResponse) SmithyHttpMessage.super.withAddedHeaders(fieldAndValues);
    }

    /**
     * Get the body of the response.
     *
     * @return Returns the response body.
     */
    ContentStream body();

    /**
     * Create a new response from this response with the given body.
     *
     * @param body Body to set.
     * @return Returns the new response.
     */
    SmithyHttpMessage withBody(ContentStream body);

    /**
     * Close underlying resources, if necessary.
     *
     * <p>If the resource is already closed, this method does nothing.
     */
    @Override
    default void close() {
        // TODO: should SmithyHttpMessage be AutoCloseable anymore?
//        try {
//            body().close();
//        } catch (IOException e) {
//            throw new UncheckedIOException(e);
//        }
    }

    static Builder builder() {
        return new Builder();
    }

    final class Builder {

        int statusCode;
        ContentStream body;
        HttpHeaders headers;
        SmithyHttpVersion httpVersion = SmithyHttpVersion.HTTP_1_1;

        private Builder() {
        }

        public Builder httpVersion(SmithyHttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder body(ContentStream body) {
            this.body = body;
            return this;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder with(SmithyHttpResponse response) {
            this.httpVersion = response.httpVersion();
            this.headers = response.headers();
            this.body = response.body();
            this.statusCode = response.statusCode();
            return this;
        }

        public SmithyHttpResponse build() {
            return new SmithyHttpResponseImpl(this);
        }
    }
}
