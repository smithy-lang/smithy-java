/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it;

import java.net.URI;
import java.net.URISyntaxException;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

public class TestUtils {
    private TestUtils() {}

    public static HttpRequest plainTextHttp11Request(String uri, String contents) {
        return plainTextRequest(HttpVersion.HTTP_1_1, uri, contents);
    }

    public static HttpRequest plainTextHttp2Request(String uri, String contents) {
        return plainTextRequest(HttpVersion.HTTP_2, uri, contents);
    }

    public static HttpRequest plainTextRequest(HttpVersion version, String uri, String contents) {
        try {
            var headers = HttpHeaders.ofModifiable();
            headers.addHeader("content-type", "text/plain");
            headers.addHeader("content-length", Long.toString(contents.length()));
            return HttpRequest.builder()
                    .httpVersion(version)
                    .uri(new URI(uri))
                    .headers(headers)
                    .method("GET")
                    .body(DataStream.ofString(contents))
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
