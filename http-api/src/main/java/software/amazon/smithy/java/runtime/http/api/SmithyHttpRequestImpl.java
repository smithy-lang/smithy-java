/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

record SmithyHttpRequestImpl(
    SmithyHttpVersion httpVersion,
    String method,
    URI uri,
    HttpHeaders headers,
    DataStream body
) implements SmithyHttpRequest {

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        // Determine the path and possible query string.
        String pathAndQuery = uri.getRawPath();
        if (uri.getRawQuery() != null) {
            pathAndQuery += "?" + uri.getRawQuery();
        }
        // Add the start line.
        result.append(method)
            .append(' ')
            .append(pathAndQuery)
            .append(' ')
            .append(httpVersion)
            .append(System.lineSeparator());
        // Append host header if not present.
        if (headers.firstValue("host") == null) {
            String host = uri.getHost();
            if (uri.getPort() != -1) {
                host += ":" + uri.getPort();
            }
            result.append("host: ").append(host).append(System.lineSeparator());
        }
        // Add other headers.
        headers.map().forEach((field, values) -> values.forEach(value -> {
            result.append(field).append(": ").append(value).append(System.lineSeparator());
        }));
        result.append(System.lineSeparator());

        return result.toString();
    }

    static final class Builder implements SmithyHttpRequest.Builder {

        String method;
        URI uri;
        DataStream body;
        HttpHeaders headers = SimpleUnmodifiableHttpHeaders.EMPTY;
        SmithyHttpVersion httpVersion = SmithyHttpVersion.HTTP_1_1;
        private Map<String, List<String>> mutatedHeaders;

        Builder() {}

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

        public SmithyHttpRequest build() {
            if (mutatedHeaders != null) {
                headers = new SimpleUnmodifiableHttpHeaders(mutatedHeaders, false);
            }
            Objects.requireNonNull(httpVersion, "HttpVersion cannot be null");
            Objects.requireNonNull(method, "Method cannot be null");
            Objects.requireNonNull(uri, "URI cannot be null");
            body = Objects.requireNonNullElse(body, DataStream.ofEmpty());
            return new SmithyHttpRequestImpl(httpVersion, method, uri, headers, body);
        }
    }
}
