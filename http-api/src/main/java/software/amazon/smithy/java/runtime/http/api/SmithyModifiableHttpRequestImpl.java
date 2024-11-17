/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.net.URI;
import java.util.Objects;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

final class SmithyModifiableHttpRequestImpl implements SmithyModifiableHttpRequest {

    private URI uri;
    private String method;
    private SmithyHttpVersion httpVersion = SmithyHttpVersion.HTTP_1_1;
    private HttpHeaders headers = new SimpleModifiableHttpHeaders();
    private DataStream body = DataStream.ofEmpty();

    @Override
    public String method() {
        return method;
    }

    @Override
    public void setMethod(String method) {
        this.method = Objects.requireNonNull(method);
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = Objects.requireNonNull(uri);
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
        return "SmithyModifiableHttpRequestImpl{"
            + "uri=" + uri
            + ", method='" + method + '\''
            + ", httpVersion=" + httpVersion
            + ", headers=" + headers
            + ", body=" + body + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SmithyModifiableHttpRequestImpl that = (SmithyModifiableHttpRequestImpl) o;
        return uri.equals(that.uri)
            && method.equals(that.method)
            && httpVersion == that.httpVersion
            && headers.equals(that.headers)
            && body.equals(that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, method, httpVersion, headers, body);
    }
}
