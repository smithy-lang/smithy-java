/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpHeaders;

public interface SmithyHttpRequest extends SmithyHttpMessage, AutoCloseable {

    String method();

    SmithyHttpRequest withMethod(String method);

    URI uri();

    SmithyHttpRequest withUri(URI uri);

    ContentStream body();

    SmithyHttpRequest withBody(ContentStream stream);

    @Override
    SmithyHttpMessage withHeaders(HttpHeaders headers);

    @Override
    default SmithyHttpRequest withHeaders(String... fieldAndValues) {
        return (SmithyHttpRequest) SmithyHttpMessage.super.withHeaders(fieldAndValues);
    }

    /**
     * Close underlying resources, if necessary.
     *
     * <p>If the resource is already closed, this method does nothing.
     */
    @Override
    default void close() {
        try {
            body().inputStream().close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    default String startLine() {
        return method() + " " + uri().getHost() + " " + httpVersion();
    }

    static Builder builder() {
        return new Builder();
    }

    final class Builder {

        String method;
        URI uri;
        ContentStream body;
        HttpHeaders headers;
        SmithyHttpVersion httpVersion = SmithyHttpVersion.HTTP_1_1;

        private Builder() {}

        public Builder httpVersion(SmithyHttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder uri(URI uri) {
            this.uri = uri;
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

        public Builder with(SmithyHttpRequest request) {
            this.httpVersion = request.httpVersion();
            this.headers = request.headers();
            this.body = request.body();
            this.method = request.method();
            this.uri = request.uri();
            return this;
        }

        public SmithyHttpRequest build() {
            return new SmithyHttpRequestImpl(this);
        }
    }
}
