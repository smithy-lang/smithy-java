/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.net.URI;
import java.util.Objects;
import software.amazon.smithy.java.io.datastream.DataStream;

final class ModifiableHttpRequestImpl implements ModifiableHttpRequest {

    private URI uri;
    private String method;
    private HttpVersion httpVersion = HttpVersion.HTTP_1_1;
    private ModifiableHttpHeaders headers = new ArrayHttpHeaders();
    private DataStream body = DataStream.ofEmpty();

    ModifiableHttpRequestImpl() {}

    ModifiableHttpRequestImpl(ModifiableHttpRequestImpl copy) {
        this.httpVersion = copy.httpVersion;
        this.method = copy.method;
        this.uri = copy.uri;
        this.headers = copy.headers.copy();
        this.body = copy.body;
    }

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
            ModifiableHttpResponseImpl.addBodyHeaders(body, headers);
        }
    }

    @Override
    public HttpRequest toUnmodifiable() {
        return new HttpRequestImpl(this);
    }

    @Override
    public ModifiableHttpRequest copy() {
        return new ModifiableHttpRequestImpl(this);
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
        ModifiableHttpRequestImpl that = (ModifiableHttpRequestImpl) o;
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
