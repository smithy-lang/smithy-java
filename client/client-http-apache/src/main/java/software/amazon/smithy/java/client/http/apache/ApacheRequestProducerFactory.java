/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import java.net.URI;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;

final class ApacheRequestProducerFactory {
    private ApacheRequestProducerFactory() {}

    static BasicRequestProducer create(HttpRequest request) {
        var apacheRequest = createRequest(request);
        request.headers().forEachEntry((name, value) -> {
            if (name != HeaderName.CONTENT_LENGTH.name()) {
                apacheRequest.addHeader(name, value);
            }
        });

        var body = request.body();
        if (body == null || (body.hasKnownLength() && body.contentLength() == 0)) {
            return new BasicRequestProducer(apacheRequest, null);
        }
        return new BasicRequestProducer(apacheRequest, createEntityProducer(body));
    }

    static BasicHttpRequest createRequest(HttpRequest request) {
        URI uri = URI.create(request.uri().toString());
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            path = path + "?" + uri.getRawQuery();
        }

        var authority = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
        var apacheRequest = new BasicHttpRequest(request.method(), authority, path);
        apacheRequest.setScheme(uri.getScheme());
        return apacheRequest;
    }

    static AsyncEntityProducer createEntityProducer(DataStream body) {
        if (body.isReplayable() && body.hasKnownLength() && body.hasByteBuffer()) {
            return new ByteBufferEntityProducer(body);
        }
        return new DataStreamEntityProducer(body);
    }

    static ContentType toApacheContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        return ContentType.parseLenient(contentType);
    }
}
