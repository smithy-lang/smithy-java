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
    private ModifiableHttpHeaders headers = new SimpleModifiableHttpHeaders();
    private DataStream body = DataStream.ofEmpty();

    ModifiableHttpResponseImpl() {}

    ModifiableHttpResponseImpl(ModifiableHttpResponseImpl copy) {
        this.httpVersion = copy.httpVersion;
        this.statusCode = copy.statusCode;
        this.headers = copy.headers.copy();
        this.body = copy.body;
    }

    @Override
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
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
    public void setHttpVersion(HttpVersion httpVersion) {
        this.httpVersion = Objects.requireNonNull(httpVersion);
    }

    @Override
    public ModifiableHttpHeaders headers() {
        return headers;
    }

    @Override
    public void setHeaders(ModifiableHttpHeaders headers) {
        this.headers = Objects.requireNonNull(headers);
    }

    @Override
    public DataStream body() {
        return body;
    }

    @Override
    public void setBody(DataStream body) {
        if (body == null) {
            this.body = DataStream.ofEmpty();
        } else {
            this.body = body;
            addBodyHeaders(body, headers);
        }
    }

    // Shared helper method with ModifiableHttpRequestImpl to set headers based on the provided body.
    static void addBodyHeaders(DataStream body, ModifiableHttpHeaders headers) {
        var ct = body.contentType();
        if (ct != null) {
            headers.setHeaderIfAbsent("content-type", ct);
        }
        if (body.hasKnownLength()) {
            headers.setHeaderIfAbsent("content-length", String.valueOf(body.contentLength()));
        }
    }

    @Override
    public HttpResponse toUnmodifiable() {
        return new HttpResponseImpl(this);
    }

    @Override
    public ModifiableHttpResponse copy() {
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
