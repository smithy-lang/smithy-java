/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

record HttpRequestImpl(
        HttpVersion httpVersion,
        String method,
        SmithyUri uri,
        HttpHeaders headers,
        DataStream body) implements HttpRequest {

    HttpRequestImpl(ModifiableHttpRequestImpl from) {
        this(from.httpVersion(), from.method(), from.uri(), from.headers().toUnmodifiable(), from.body());
    }

    @Override
    public HttpRequest toUnmodifiable() {
        return this;
    }

    @Override
    public ModifiableHttpRequest toModifiable() {
        return toModifiableCopy();
    }

    @Override
    public ModifiableHttpRequest toModifiableCopy() {
        var mod = new ModifiableHttpRequestImpl();
        mod.setHttpVersion(httpVersion);
        mod.setMethod(method);
        mod.setUri(uri);
        mod.setHeaders(headers.toModifiable());
        mod.setBody(body);
        return mod;
    }
}
