/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.io.datastream.DataStream;

record HttpRequestImpl(
        HttpVersion httpVersion,
        String method,
        URI uri,
        HttpHeaders headers,
        DataStream body) implements HttpRequest {

    HttpRequestImpl(ModifiableHttpRequestImpl from) {
        this(from.httpVersion(), from.method(), from.uri(), from.headers().toUnmodifiable(), from.body());
    }

    @Override
    public HttpRequest toUnmodifiable() {
        return this;
    }

    @Override
    public ModifiableHttpRequest toModifiable() {
        var mod = new ModifiableHttpRequestImpl();
        mod.setHttpVersion(httpVersion);
        mod.setMethod(method);
        mod.setUri(uri);
        mod.setHeaders(headers.toModifiable());
        mod.setBody(body);
        return mod;
    }

    static final class Builder implements HttpRequest.Builder {
        private final ModifiableHttpRequest modifiableHttpRequest = new ModifiableHttpRequestImpl();

        Builder() {}

        public Builder httpVersion(HttpVersion httpVersion) {
            modifiableHttpRequest.setHttpVersion(httpVersion);
            return this;
        }

        public Builder method(String method) {
            modifiableHttpRequest.setMethod(method);
            return this;
        }

        public Builder uri(URI uri) {
            modifiableHttpRequest.setUri(uri);
            return this;
        }

        public Builder body(Flow.Publisher<ByteBuffer> publisher) {
            return body(DataStream.ofPublisher(publisher, null, -1));
        }

        public Builder body(DataStream body) {
            modifiableHttpRequest.setBody(body);
            return this;
        }

        @Override
        public HttpRequest.Builder headers(HttpHeaders headers) {
            modifiableHttpRequest.setHeaders(headers.toModifiable());
            return this;
        }

        @Override
        public Builder withAddedHeader(String name, String value) {
            modifiableHttpRequest.headers().addHeader(name, value);
            return this;
        }

        @Override
        public Builder withAddedHeaders(Map<String, List<String>> headers) {
            modifiableHttpRequest.headers().addHeaders(headers);
            return this;
        }

        @Override
        public Builder withReplacedHeaders(Map<String, List<String>> headers) {
            modifiableHttpRequest.headers().setHeaders(headers);
            return this;
        }

        private void beforeBuild() {
            Objects.requireNonNull(modifiableHttpRequest.method(), "method not set");
            Objects.requireNonNull(modifiableHttpRequest.uri(), "uri not set");
        }

        @Override
        public HttpRequest build() {
            beforeBuild();
            return modifiableHttpRequest.toUnmodifiable();
        }

        @Override
        public ModifiableHttpRequest buildModifiable() {
            beforeBuild();
            return modifiableHttpRequest.copy();
        }
    }
}
