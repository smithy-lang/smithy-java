/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import software.amazon.smithy.java.io.datastream.DataStream;

record HttpResponseImpl(
        HttpVersion httpVersion,
        int statusCode,
        HttpHeaders headers,
        DataStream body) implements HttpResponse {

    HttpResponseImpl(ModifiableHttpResponse from) {
        this(from.httpVersion(), from.statusCode(), from.headers().toUnmodifiable(), from.body());
    }

    @Override
    public HttpResponse toUnmodifiable() {
        return this;
    }

    @Override
    public ModifiableHttpResponse toModifiable() {
        return toModifiableCopy();
    }

    @Override
    public ModifiableHttpResponse toModifiableCopy() {
        var mod = new ModifiableHttpResponseImpl();
        mod.setHttpVersion(httpVersion);
        mod.setStatusCode(statusCode);
        mod.setHeaders(headers.toModifiable());
        mod.setBody(body);
        return mod;
    }
}
