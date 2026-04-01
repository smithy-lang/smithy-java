/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.Objects;
import software.amazon.smithy.java.io.datastream.DataStream;

final class ModifiableHttpResponseImpl implements ModifiableHttpResponse {

    private int statusCode = 200;
    private HttpVersion httpVersion = HttpVersion.HTTP_1_1;
    private ModifiableHttpHeaders headers = new ArrayHttpHeaders();
    private DataStream body = DataStream.ofEmpty();

    ModifiableHttpResponseImpl() {}

    ModifiableHttpResponseImpl(ModifiableHttpResponseImpl copy) {
        this.httpVersion = copy.httpVersion;
        this.statusCode = copy.statusCode;
        this.headers = copy.headers.copy();
        this.body = copy.body;
    }

    @Override
    public ModifiableHttpResponse setStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public HttpVersion httpVersion() {
        return httpVersion;
    }

    @Override
    public ModifiableHttpResponse setHttpVersion(HttpVersion httpVersion) {
        this.httpVersion = Objects.requireNonNull(httpVersion);
        return this;
    }

    @Override
    public ModifiableHttpHeaders headers() {
        return headers;
    }

    @Override
    public ModifiableHttpResponse setHeaders(ModifiableHttpHeaders headers) {
        this.headers = Objects.requireNonNull(headers);
        return this;
    }

    @Override
    public DataStream body() {
        return body;
    }

    @Override
    public ModifiableHttpResponse setBody(DataStream body) {
        if (body == null) {
            this.body = DataStream.ofEmpty();
        } else {
            this.body = body;
            addBodyHeaders(body, headers);
        }
        return this;
    }

    // Shared helper method with ModifiableHttpRequestImpl to set headers based on the provided body.
    static void addBodyHeaders(DataStream body, ModifiableHttpHeaders headers) {
        var ct = body.contentType();
        if (ct != null) {
            headers.setHeaderIfAbsent(HeaderNames.CONTENT_TYPE, ct);
        }
        if (body.hasKnownLength()) {
            headers.setHeaderIfAbsent(HeaderNames.CONTENT_LENGTH, String.valueOf(body.contentLength()));
        }
    }

    @Override
    public HttpResponse toUnmodifiable() {
        return new HttpResponseImpl(this);
    }

    @Override
    public ModifiableHttpResponse toModifiableCopy() {
        return new ModifiableHttpResponseImpl(this);
    }

    @Override
    public String toString() {
        return "SmithyModifiableHttpResponseImpl{"
                + "body=" + body
                + ", statusCode=" + statusCode
                + ", httpVersion=" + httpVersion
                + ", headers=" + headers + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModifiableHttpResponseImpl that = (ModifiableHttpResponseImpl) o;
        return statusCode == that.statusCode
                && httpVersion == that.httpVersion
                && headers.equals(that.headers)
                && body.equals(that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpVersion, statusCode, headers, body);
    }
}
