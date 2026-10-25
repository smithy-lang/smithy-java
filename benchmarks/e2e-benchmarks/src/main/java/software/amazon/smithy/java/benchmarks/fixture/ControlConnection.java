/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.fixture;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** One control request per connection: {@code POST /fixture} whose body is the response to serve next. */
final class ControlConnection {

    private static final byte[] NO_CONTENT =
            FixtureServer.ascii("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    private static final byte[] BAD_REQUEST =
            FixtureServer.ascii("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    private static final byte[] NOT_FOUND =
            FixtureServer.ascii("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    private static final int MAX_HEAD = 8 * 1024;
    private static final long MAX_BODY = 64L * 1024 * 1024;

    private ControlConnection() {}

    static void handle(Socket socket, FixtureServer server) throws IOException {
        try (socket) {
            socket.setSoTimeout(10_000);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            String head = readHead(in);
            if (head == null) {
                out.write(BAD_REQUEST);
                return;
            }
            String[] lines = head.split("\r\n");
            if (!lines[0].startsWith("POST " + FixtureServer.CONTROL_PATH + " ")) {
                out.write(NOT_FOUND);
                return;
            }
            long contentLength = contentLength(lines);
            if (contentLength < 0 || contentLength > MAX_BODY) {
                out.write(BAD_REQUEST);
                return;
            }
            byte[] fixture = in.readNBytes((int) contentLength);
            if (fixture.length != contentLength || !FixtureServer.isValidResponse(fixture)) {
                out.write(BAD_REQUEST);
                return;
            }
            server.respondWith(fixture);
            out.write(NO_CONTENT);
            out.flush();
        }
    }

    /** Returns the request head without its final CRLF CRLF, or null if the stream ends or the head is too large. */
    private static String readHead(InputStream in) throws IOException {
        var head = new ByteArrayOutputStream(512);
        int trailing = 0;
        int c;
        while ((c = in.read()) >= 0) {
            head.write(c);
            if (head.size() > MAX_HEAD) {
                return null;
            }
            trailing =
                    (c == '\r' && (trailing == 0 || trailing == 2)) || (c == '\n' && (trailing == 1 || trailing == 3))
                            ? trailing + 1
                            : (c == '\r' ? 1 : 0);
            if (trailing == 4) {
                return head.toString(StandardCharsets.ISO_8859_1).substring(0, head.size() - 4);
            }
        }
        return null;
    }

    private static long contentLength(String[] lines) {
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0 && lines[i].substring(0, colon).strip().equalsIgnoreCase("content-length")) {
                try {
                    return Long.parseLong(lines[i].substring(colon + 1).strip());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
