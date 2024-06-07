/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;

public final class SmithyHttpResponseImpl implements SmithyHttpResponse {

    private final SmithyHttpVersion httpVersion;
    private final int statusCode;
    private final Flow.Publisher<ByteBuffer> body;
    private final HttpHeaders headers;

    SmithyHttpResponseImpl(SmithyHttpResponse.Builder builder) {
        this.httpVersion = Objects.requireNonNull(builder.httpVersion);
        this.statusCode = builder.statusCode;
        this.body = Objects.requireNonNullElse(builder.body, HttpRequest.BodyPublishers.noBody());
        this.headers = Objects.requireNonNullElseGet(builder.headers, () -> HttpHeaders.of(Map.of(), (k, v) -> true));
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(httpVersion).append(' ').append(statusCode).append(System.lineSeparator());
        headers.map().forEach((field, values) -> values.forEach(value -> {
            result.append(field).append(": ").append(value).append(System.lineSeparator());
        }));
        result.append(System.lineSeparator());
        return result.toString();
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public SmithyHttpResponse withStatusCode(int statusCode) {
        return SmithyHttpResponse.builder().with(this).statusCode(statusCode).build();
    }

    @Override
    public SmithyHttpVersion httpVersion() {
        return httpVersion;
    }

    @Override
    public SmithyHttpResponse withHttpVersion(SmithyHttpVersion httpVersion) {
        return SmithyHttpResponse.builder().with(this).httpVersion(httpVersion).build();
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public SmithyHttpResponse withHeaders(HttpHeaders headers) {
        return SmithyHttpResponse.builder().with(this).headers(headers).build();
    }

    @Override
    public Flow.Publisher<ByteBuffer> body() {
        return body;
    }

    @Override
    public SmithyHttpResponse withBody(Flow.Publisher<ByteBuffer> body) {
        return SmithyHttpResponse.builder().with(this).body(body).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SmithyHttpResponseImpl that = (SmithyHttpResponseImpl) o;
        return statusCode == that.statusCode && httpVersion == that.httpVersion && Objects.equals(body, that.body)
            && Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpVersion, statusCode, body, headers);
    }
}
