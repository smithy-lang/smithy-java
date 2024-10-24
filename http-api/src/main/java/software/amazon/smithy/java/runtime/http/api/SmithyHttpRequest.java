/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

public interface SmithyHttpRequest extends SmithyHttpMessage {

    String method();

    SmithyHttpRequest withMethod(String method);

    URI uri();

    SmithyHttpRequest withUri(URI uri);

    @Override
    SmithyHttpRequest withHttpVersion(SmithyHttpVersion version);

    @Override
    SmithyHttpRequest withHeaders(HttpHeaders headers);

    @Override
    default SmithyHttpRequest withAddedHeaders(HttpHeaders headers) {
        return (SmithyHttpRequest) SmithyHttpMessage.super.withAddedHeaders(headers);
    }

    @Override
    default SmithyHttpRequest withAddedHeaders(String... fieldAndValues) {
        return (SmithyHttpRequest) SmithyHttpMessage.super.withAddedHeaders(fieldAndValues);
    }

    @Override
    SmithyHttpRequest withBody(DataStream stream);

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
        DataStream body;
        HttpHeaders headers = SimpleUnmodifiableHttpHeaders.EMPTY;
        SmithyHttpVersion httpVersion = SmithyHttpVersion.HTTP_1_1;

        private Builder() {
        }

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

        public Builder body(Flow.Publisher<ByteBuffer> publisher) {
            return body(DataStream.ofPublisher(publisher, null, -1));
        }

        public Builder body(DataStream body) {
            this.body = body;
            return this;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = Objects.requireNonNull(headers);
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
