/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.fixture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Prepares response bytes once for HEAD requests and Connection: close. */
final class Fixture {

    static final byte[] CONTINUE_100 = ascii("HTTP/1.1 100 Continue\r\n\r\n");
    static final byte[] BAD_REQUEST_400 =
            ascii("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    static final byte[] HEADERS_TOO_LARGE_431 =
            ascii("HTTP/1.1 431 Request Header Fields Too Large\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");

    private static final Map<Integer, String> REASONS = Map.ofEntries(
            Map.entry(200, "OK"),
            Map.entry(201, "Created"),
            Map.entry(202, "Accepted"),
            Map.entry(204, "No Content"),
            Map.entry(205, "Reset Content"),
            Map.entry(304, "Not Modified"),
            Map.entry(400, "Bad Request"),
            Map.entry(403, "Forbidden"),
            Map.entry(404, "Not Found"),
            Map.entry(409, "Conflict"),
            Map.entry(429, "Too Many Requests"),
            Map.entry(500, "Internal Server Error"),
            Map.entry(503, "Service Unavailable"));

    private final int status;
    private final byte[] body;
    private final byte[] full;
    private final byte[] fullClose;
    private final byte[] headOnly;
    private final byte[] headOnlyClose;

    private Fixture(int status, byte[] body, byte[] head, byte[] headClose) {
        this.status = status;
        this.body = body;
        this.full = concat(head, body);
        this.fullClose = concat(headClose, body);
        this.headOnly = head;
        this.headOnlyClose = headClose;
    }

    static Fixture load(Path bodyFile, int status, String contentType, List<Map.Entry<String, String>> fixedHeaders)
            throws IOException {
        if (status < 200 || status > 599) {
            throw new IllegalArgumentException("--status must be between 200 and 599, got " + status);
        }
        byte[] body = Files.readAllBytes(bodyFile);
        boolean bodiless = status == 204 || status == 205 || status == 304;
        if (bodiless && body.length > 0) {
            throw new IllegalArgumentException("status " + status + " requires an empty body, got " + body.length
                    + " bytes in " + bodyFile);
        }
        // Omit Content-Length for 204 and 304. Keep an explicit zero for 205.
        boolean contentLength = status != 204 && status != 304;
        for (var header : fixedHeaders) {
            String name = header.getKey().toLowerCase(Locale.ROOT);
            if (name.equals("content-length") || name.equals("transfer-encoding")
                    || name.equals("connection")
                    || name.equals("content-type")) {
                throw new IllegalArgumentException("header " + header.getKey()
                        + " is managed by the server; set the content type with --content-type");
            }
        }
        return new Fixture(status,
                body,
                head(status, contentType, body.length, contentLength, fixedHeaders, false),
                head(status, contentType, body.length, contentLength, fixedHeaders, true));
    }

    int status() {
        return status;
    }

    byte[] body() {
        return body;
    }

    byte[] response(boolean headRequest, boolean close) {
        if (headRequest) {
            return close ? headOnlyClose : headOnly;
        }
        return close ? fullClose : full;
    }

    private static byte[] head(
            int status,
            String contentType,
            int length,
            boolean contentLength,
            List<Map.Entry<String, String>> fixedHeaders,
            boolean close
    ) {
        var sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(status).append(' ').append(REASONS.getOrDefault(status, "Status")).append("\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        if (contentLength) {
            sb.append("Content-Length: ").append(length).append("\r\n");
        }
        for (var header : fixedHeaders) {
            sb.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        if (close) {
            sb.append("Connection: close\r\n");
        }
        sb.append("\r\n");
        return ascii(sb.toString());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}
