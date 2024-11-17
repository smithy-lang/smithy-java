/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

record SmithyHttpResponseImpl(
    SmithyHttpVersion httpVersion,
    int statusCode,
    HttpHeaders headers,
    DataStream body
) implements SmithyHttpResponse {

    @Override
    public SmithyModifiableHttpResponse toModifiable() {
        var mod = new SmithyModifiableHttpResponseImpl();
        mod.setHttpVersion(httpVersion);
        mod.setStatusCode(statusCode);
        mod.setHeaders(headers);
        mod.setBody(body);
        return mod;
    }

    static final class Builder implements SmithyHttpResponse.Builder {
        int statusCode;
        DataStream body;
        HttpHeaders headers = SimpleUnmodifiableHttpHeaders.EMPTY;
        SmithyHttpVersion httpVersion = SmithyHttpVersion.HTTP_1_1;
        private Map<String, List<String>> mutatedHeaders;

        Builder() {}

        @Override
        public Builder httpVersion(SmithyHttpVersion httpVersion) {
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
        public SmithyHttpResponse build() {
            beforeBuild();
            return new SmithyHttpResponseImpl(httpVersion, statusCode, headers, body);
        }

        @Override
        public SmithyModifiableHttpResponse buildModifiable() {
            beforeBuild();
            var mod = new SmithyModifiableHttpResponseImpl();
            mod.setHttpVersion(httpVersion);
            mod.setStatusCode(statusCode);
            mod.setHeaders(headers);
            mod.setBody(body);
            return mod;
        }
    }
}
