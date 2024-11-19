/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.io.datastream.DataStream;

record HttpResponseImpl(
    HttpVersion httpVersion,
    int statusCode,
    HttpHeaders headers,
    DataStream body
) implements HttpResponse {

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
        int statusCode;
        DataStream body;
        HttpHeaders headers = SimpleUnmodifiableHttpHeaders.EMPTY;
        HttpVersion httpVersion = HttpVersion.HTTP_1_1;
        private Map<String, List<String>> mutatedHeaders;

        Builder() {}

        @Override
        public Builder httpVersion(HttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        @Override
        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        @Override
        public Builder body(DataStream body) {
            this.body = body;
            return this;
        }

        @Override
        public Builder headers(HttpHeaders headers) {
            this.headers = Objects.requireNonNull(headers);
            mutatedHeaders = null;
            return this;
        }

        @Override
        public Builder withAddedHeaders(String... headers) {
            mutatedHeaders = SimpleUnmodifiableHttpHeaders.addHeaders(this.headers, mutatedHeaders, headers);
            return this;
        }

        @Override
        public Builder withAddedHeaders(HttpHeaders headers) {
            mutatedHeaders = SimpleUnmodifiableHttpHeaders.addHeaders(this.headers, mutatedHeaders, headers);
            return this;
        }

        @Override
        public Builder withReplacedHeaders(Map<String, List<String>> headers) {
            mutatedHeaders = SimpleUnmodifiableHttpHeaders.replaceHeaders(this.headers, mutatedHeaders, headers);
            return this;
        }

        private void beforeBuild() {
            if (statusCode == 0) {
                throw new IllegalStateException("No status code was set on response");
            }
            if (mutatedHeaders != null) {
                headers = new SimpleUnmodifiableHttpHeaders(mutatedHeaders, false);
            }
            Objects.requireNonNull(httpVersion);
            body = Objects.requireNonNullElse(body, DataStream.ofEmpty());
        }

        @Override
        public HttpResponse build() {
            beforeBuild();
            return new HttpResponseImpl(httpVersion, statusCode, headers, body);
        }

        @Override
        public ModifiableHttpResponse buildModifiable() {
            beforeBuild();
            var mod = new ModifiableHttpResponseImpl();
            mod.setHttpVersion(httpVersion);
            mod.setStatusCode(statusCode);
            mod.setHeaders(headers.toModifiable());
            mod.setBody(body);
            return mod;
        }
    }
}
