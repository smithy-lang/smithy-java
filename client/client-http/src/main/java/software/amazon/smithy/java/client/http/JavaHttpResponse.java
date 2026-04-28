/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.api.ModifiableHttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

record JavaHttpResponse(
        HttpVersion httpVersion,
        int statusCode,
        HttpHeaders headers,
        DataStream body) implements HttpResponse {

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
        return HttpResponse.create()
                .setHttpVersion(httpVersion)
                .setStatusCode(statusCode)
                .setHeaders(headers.toModifiable())
                .setBody(body);
    }
}
