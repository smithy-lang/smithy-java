/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

record CannedResponse(
        int statusCode,
        HttpHeaders headers,
        Map<String, List<String>> headerMap,
        ByteBuffer body,
        String contentType) {

    static CannedResponse of(int statusCode, Map<String, String> caseHeaders, byte[] body, String defaultContentType) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("content-type", List.of(defaultContentType));
        headers.put("content-length", List.of(Integer.toString(body.length)));
        for (var entry : caseHeaders.entrySet()) {
            headers.put(entry.getKey().toLowerCase(Locale.ROOT), List.of(entry.getValue()));
        }
        String contentType = headers.get("content-type").get(0);
        return new CannedResponse(
                statusCode,
                HttpHeaders.of(headers),
                Collections.unmodifiableMap(headers),
                ByteBuffer.wrap(body),
                contentType);
    }

    HttpResponse newHttpResponse() {
        return HttpResponse.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .statusCode(statusCode)
                .headers(headers)
                .body(DataStream.ofByteBuffer(body.duplicate(), contentType))
                .build();
    }

    int bodyLength() {
        return body.remaining();
    }

    /** The response as HTTP/1.1 wire bytes, which is what the fixture server writes for every request. */
    byte[] toHttp1Bytes() {
        var head = new StringBuilder(256);
        head.append("HTTP/1.1 ").append(statusCode).append(' ').append(reason(statusCode)).append("\r\n");
        for (var entry : headerMap.entrySet()) {
            for (String value : entry.getValue()) {
                head.append(entry.getKey()).append(": ").append(value).append("\r\n");
            }
        }
        head.append("\r\n");
        byte[] headBytes = head.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] wire = new byte[headBytes.length + body.remaining()];
        System.arraycopy(headBytes, 0, wire, 0, headBytes.length);
        body.duplicate().get(wire, headBytes.length, body.remaining());
        return wire;
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            default -> "Status";
        };
    }
}
