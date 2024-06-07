/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public interface SmithyHttpResponse extends SmithyHttpMessage {

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

    @Override
    SmithyHttpResponse withBody(Flow.Publisher<ByteBuffer> body);

    static Builder builder() {
        return new Builder();
    }

    final class Builder {

        int statusCode;
        Flow.Publisher<ByteBuffer> body;
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

        public Builder body(Flow.Publisher<ByteBuffer> body) {
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
