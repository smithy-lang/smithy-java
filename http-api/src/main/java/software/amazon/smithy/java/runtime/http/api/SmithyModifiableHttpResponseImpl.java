/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.util.Objects;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

final class SmithyModifiableHttpResponseImpl implements SmithyModifiableHttpResponse {

    private int statusCode = 200;
    private SmithyHttpVersion httpVersion = SmithyHttpVersion.HTTP_1_1;
    private HttpHeaders headers = new SimpleModifiableHttpHeaders();
    private DataStream body = DataStream.ofEmpty();

    @Override
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public SmithyHttpVersion httpVersion() {
        return httpVersion;
    }

    @Override
    public void setHttpVersion(SmithyHttpVersion httpVersion) {
        this.httpVersion = Objects.requireNonNull(httpVersion);
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public void setHeaders(HttpHeaders headers) {
        this.headers = Objects.requireNonNull(headers);
    }

    @Override
    public DataStream body() {
        return body;
    }

    @Override
    public void setBody(DataStream body) {
        this.body = Objects.requireNonNull(body);
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
        SmithyModifiableHttpResponseImpl that = (SmithyModifiableHttpResponseImpl) o;
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
