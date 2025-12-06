/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.io.datastream.DataStream;

record HttpResponseImpl(
        HttpVersion httpVersion,
        int statusCode,
        HttpHeaders headers,
        DataStream body) implements HttpResponse {

    HttpResponseImpl(ModifiableHttpResponse from) {
        this(from.httpVersion(), from.statusCode(), from.headers().toUnmodifiable(), from.body());
    }

    @Override
    public HttpResponse toUnmodifiable() {
        return this;
    }

    @Override
    public ModifiableHttpResponse toModifiable() {
        var mod = new ModifiableHttpResponseImpl();
        mod.setHttpVersion(httpVersion);
        mod.setStatusCode(statusCode);
        mod.setHeaders(headers.toModifiable());
        mod.setBody(body);
        return mod;
    }

    static final class Builder implements HttpResponse.Builder {
        ModifiableHttpResponseImpl modifiableResponse = new ModifiableHttpResponseImpl();

        Builder() {}

        @Override
        public Builder httpVersion(HttpVersion httpVersion) {
            modifiableResponse.setHttpVersion(httpVersion);
            return this;
        }

        @Override
        public Builder statusCode(int statusCode) {
            modifiableResponse.setStatusCode(statusCode);
            return this;
        }

        @Override
        public Builder body(DataStream body) {
            modifiableResponse.setBody(body);
            return this;
        }

        @Override
        public Builder headers(HttpHeaders headers) {
            modifiableResponse.setHeaders(headers.toModifiable());
            return this;
        }

        @Override
        public Builder withAddedHeader(String name, String value) {
            modifiableResponse.headers().addHeader(name, value);
            return this;
        }

        @Override
        public Builder withAddedHeaders(Map<String, List<String>> headers) {
            modifiableResponse.headers().addHeaders(headers);
            return this;
        }

        @Override
        public Builder withReplacedHeaders(Map<String, List<String>> headers) {
            modifiableResponse.headers().setHeaders(headers);
            return this;
        }

        @Override
        public HttpResponse build() {
            return modifiableResponse.toUnmodifiable();
        }

        @Override
        public ModifiableHttpResponse buildModifiable() {
            return modifiableResponse.copy();
        }
    }
}
