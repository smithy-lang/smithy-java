/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.common.datastream.DataStream;

final class SmithyHttpRequestImpl implements SmithyHttpRequest {

    private final SmithyHttpVersion httpVersion;
    private final String method;
    private final URI uri;
    private final DataStream body;
    private final HttpHeaders headers;

    SmithyHttpRequestImpl(SmithyHttpRequest.Builder builder) {
        this.httpVersion = Objects.requireNonNull(builder.httpVersion);
        this.method = Objects.requireNonNull(builder.method);
        this.uri = Objects.requireNonNull(builder.uri);
        this.body = Objects.requireNonNullElse(builder.body, DataStream.ofEmpty());
        this.headers = Objects.requireNonNullElseGet(builder.headers, () -> HttpHeaders.of(Map.of(), (k, v) -> true));
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public SmithyHttpRequest withMethod(String method) {
        return SmithyHttpRequest.builder().with(this).method(method).build();
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public SmithyHttpRequest withUri(URI uri) {
        return SmithyHttpRequest.builder().with(this).uri(uri).build();
    }

    @Override
    public SmithyHttpVersion httpVersion() {
        return httpVersion;
    }

    @Override
    public SmithyHttpRequest withHttpVersion(SmithyHttpVersion httpVersion) {
        return SmithyHttpRequest.builder().with(this).httpVersion(httpVersion).build();
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public SmithyHttpRequest withHeaders(HttpHeaders headers) {
        return SmithyHttpRequest.builder().with(this).headers(headers).build();
    }

    @Override
    public DataStream body() {
        return body;
    }

    @Override
    public SmithyHttpRequest withBody(DataStream body) {
        return SmithyHttpRequest.builder().with(this).body(body).build();
    }

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
        if (!headers.firstValue("host").isPresent()) {
            String host = uri.getHost();
            if (uri.getPort() != -1) {
                host += ":" + uri.getPort();
            }
            result.append("Host: ").append(host).append(System.lineSeparator());
        }
        // Add other headers.
        headers.map().forEach((field, values) -> values.forEach(value -> {
            result.append(field).append(": ").append(value).append(System.lineSeparator());
        }));
        result.append(System.lineSeparator());

        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SmithyHttpRequestImpl that = (SmithyHttpRequestImpl) o;
        return httpVersion == that.httpVersion && method.equals(that.method) && uri.equals(that.uri)
            && body.equals(that.body) && headers.equals(that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpVersion, method, uri, body, headers);
    }
}
