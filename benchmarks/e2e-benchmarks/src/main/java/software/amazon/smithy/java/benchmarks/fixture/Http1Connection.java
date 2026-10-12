/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.fixture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import software.amazon.smithy.java.http.client.connection.ConnectionTransport;

/**
 * Serves one connection: frame each request in a reusable buffer, drain its body, write the current response.
 * Only the headers that affect framing are parsed. Malformed framing gets a 400 and a close.
 */
final class Http1Connection implements Runnable {

    private static final byte[] BAD_REQUEST =
            FixtureServer.ascii("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    private static final byte[] CONTENT_LENGTH = FixtureServer.ascii("content-length");
    private static final byte[] TRANSFER_ENCODING = FixtureServer.ascii("transfer-encoding");
    private static final byte[] CONNECTION = FixtureServer.ascii("connection");
    private static final byte[] CHUNKED = FixtureServer.ascii("chunked");
    private static final byte[] CLOSE = FixtureServer.ascii("close");
    private static final byte[] HTTP_1_1 = FixtureServer.ascii("HTTP/1.1");

    private final ConnectionTransport transport;
    private final FixtureServer server;
    private final byte[] buf;
    private int pos;
    private int limit;
    private int scanned;
    private InputStream in;

    private boolean close;
    private boolean chunked;
    private long contentLength;
    private boolean bad;

    Http1Connection(ConnectionTransport transport, FixtureServer server, int readBuffer) {
        this.transport = transport;
        this.server = server;
        this.buf = new byte[readBuffer];
    }

    @Override
    public void run() {
        try (transport) {
            in = transport.inputStream();
            OutputStream out = transport.outputStream();
            while (true) {
                int headEnd = fillUntilHeadEnd();
                if (headEnd < 0) {
                    return;
                }
                if (headEnd == Integer.MAX_VALUE) {
                    out.write(BAD_REQUEST);
                    out.flush();
                    return;
                }
                parseHead(headEnd);
                pos = headEnd;
                scanned = headEnd;
                if (bad) {
                    out.write(BAD_REQUEST);
                    out.flush();
                    return;
                }
                if (chunked) {
                    drainChunked();
                } else if (contentLength > 0) {
                    drain(contentLength);
                }
                // The flush pushes out any TLS record the transport still buffers.
                out.write(server.response());
                out.flush();
                if (close) {
                    return;
                }
                if (pos == limit) {
                    pos = limit = scanned = 0;
                }
            }
        } catch (IOException e) {
            // A client disconnect ends the connection quietly.
        }
    }

    /** Returns the index after the headers, -1 on EOF, or Integer.MAX_VALUE if the headers exceed the buffer. */
    private int fillUntilHeadEnd() throws IOException {
        while (true) {
            int end = findHeadEnd();
            if (end >= 0) {
                return end;
            }
            if (limit == buf.length) {
                if (pos == 0) {
                    return Integer.MAX_VALUE;
                }
                compact();
            }
            int n = in.read(buf, limit, buf.length - limit);
            if (n < 0) {
                return -1;
            }
            limit += n;
        }
    }

    /** Rescans three bytes so a header terminator split across reads is still found. */
    private int findHeadEnd() {
        int i = Math.max(pos, scanned - 3);
        int stop = limit - 3;
        while (i < stop) {
            if (buf[i] == '\r' && buf[i + 1] == '\n' && buf[i + 2] == '\r' && buf[i + 3] == '\n') {
                scanned = i + 4;
                return i + 4;
            }
            i++;
        }
        scanned = limit;
        return -1;
    }

    private void parseHead(int headEnd) {
        close = false;
        chunked = false;
        contentLength = 0;
        bad = false;
        boolean sawContentLength = false;

        int lineEnd = indexOfCrlf(pos, headEnd);
        if (indexOf((byte) ' ', pos, lineEnd) < 0) {
            bad = true;
            return;
        }
        if (lineEnd - pos < 8 || !regionEquals(lineEnd - 8, HTTP_1_1)) {
            // Anything but HTTP/1.1 gets one response and a close.
            close = true;
        }

        int lineStart = lineEnd + 2;
        while (lineStart < headEnd - 2) {
            lineEnd = indexOfCrlf(lineStart, headEnd);
            int colon = indexOf((byte) ':', lineStart, lineEnd);
            if (colon < 0) {
                bad = true;
                return;
            }
            int valueStart = colon + 1;
            while (valueStart < lineEnd && (buf[valueStart] == ' ' || buf[valueStart] == '\t')) {
                valueStart++;
            }
            int valueEnd = lineEnd;
            while (valueEnd > valueStart && (buf[valueEnd - 1] == ' ' || buf[valueEnd - 1] == '\t')) {
                valueEnd--;
            }
            if (nameIs(lineStart, colon, CONTENT_LENGTH)) {
                long length = parseDecimal(valueStart, valueEnd);
                if (length < 0 || sawContentLength && length != contentLength) {
                    bad = true;
                    return;
                }
                sawContentLength = true;
                contentLength = length;
            } else if (nameIs(lineStart, colon, TRANSFER_ENCODING)) {
                if (!valueIsIgnoreCase(valueStart, valueEnd, CHUNKED)) {
                    bad = true;
                    return;
                }
                chunked = true;
            } else if (nameIs(lineStart, colon, CONNECTION)) {
                if (containsTokenIgnoreCase(valueStart, valueEnd, CLOSE)) {
                    close = true;
                }
            }
            lineStart = lineEnd + 2;
        }
        if (chunked && sawContentLength) {
            bad = true;
        }
    }

    private void drain(long length) throws IOException {
        long remaining = length;
        int buffered = limit - pos;
        if (buffered > 0) {
            int take = (int) Math.min(buffered, remaining);
            pos += take;
            remaining -= take;
        }
        if (remaining == 0) {
            return;
        }
        pos = limit = scanned = 0;
        while (remaining > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) {
                throw new IOException("EOF inside a request body");
            }
            remaining -= n;
        }
    }

    private void drainChunked() throws IOException {
        while (true) {
            int lineEnd = fillLine();
            long size = parseHex(pos, lineEnd);
            if (size < 0) {
                throw new IOException("Malformed chunk size");
            }
            pos = lineEnd + 2;
            if (size == 0) {
                break;
            }
            drain(size);
            if (fillLine() != pos) {
                throw new IOException("Missing CRLF after chunk data");
            }
            pos += 2;
        }
        // Trailers end with an empty line.
        while (true) {
            int lineEnd = fillLine();
            boolean empty = lineEnd == pos;
            pos = lineEnd + 2;
            if (empty) {
                break;
            }
        }
        scanned = pos;
    }

    private int fillLine() throws IOException {
        while (true) {
            int end = indexOfCrlf(pos, limit);
            if (end >= 0) {
                return end;
            }
            if (limit == buf.length) {
                if (pos == 0) {
                    throw new IOException("Line longer than the read buffer");
                }
                compact();
            }
            int n = in.read(buf, limit, buf.length - limit);
            if (n < 0) {
                throw new IOException("EOF inside a chunked body");
            }
            limit += n;
        }
    }

    private void compact() {
        int live = limit - pos;
        System.arraycopy(buf, pos, buf, 0, live);
        scanned = Math.max(0, scanned - pos);
        pos = 0;
        limit = live;
    }

    private int indexOfCrlf(int from, int to) {
        for (int i = from; i < to - 1; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private int indexOf(byte b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (buf[i] == b) {
                return i;
            }
        }
        return -1;
    }

    private boolean regionEquals(int from, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (buf[from + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean nameIs(int from, int to, byte[] lowerName) {
        int end = to;
        while (end > from && (buf[end - 1] == ' ' || buf[end - 1] == '\t')) {
            end--;
        }
        if (end - from != lowerName.length) {
            return false;
        }
        for (int i = 0; i < lowerName.length; i++) {
            if (lower(buf[from + i]) != lowerName[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean valueIsIgnoreCase(int from, int to, byte[] lowerValue) {
        if (to - from != lowerValue.length) {
            return false;
        }
        for (int i = 0; i < lowerValue.length; i++) {
            if (lower(buf[from + i]) != lowerValue[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsTokenIgnoreCase(int from, int to, byte[] lowerToken) {
        int start = from;
        while (start < to) {
            int end = start;
            while (end < to && buf[end] != ',') {
                end++;
            }
            int s = start;
            int e = end;
            while (s < e && (buf[s] == ' ' || buf[s] == '\t')) {
                s++;
            }
            while (e > s && (buf[e - 1] == ' ' || buf[e - 1] == '\t')) {
                e--;
            }
            if (valueIsIgnoreCase(s, e, lowerToken)) {
                return true;
            }
            start = end + 1;
        }
        return false;
    }

    private long parseDecimal(int from, int to) {
        if (from == to) {
            return -1;
        }
        long value = 0;
        for (int i = from; i < to; i++) {
            int d = buf[i] - '0';
            if (d < 0 || d > 9 || value > (Long.MAX_VALUE - d) / 10) {
                return -1;
            }
            value = value * 10 + d;
        }
        return value;
    }

    private long parseHex(int from, int to) {
        long value = 0;
        int i = from;
        for (; i < to; i++) {
            byte b = buf[i];
            int d;
            if (b >= '0' && b <= '9') {
                d = b - '0';
            } else if (b >= 'a' && b <= 'f') {
                d = b - 'a' + 10;
            } else if (b >= 'A' && b <= 'F') {
                d = b - 'A' + 10;
            } else if (b == ';' || b == ' ' || b == '\t') {
                break;
            } else {
                return -1;
            }
            if (value > (Long.MAX_VALUE >> 4)) {
                return -1;
            }
            value = (value << 4) | d;
        }
        return i == from ? -1 : value;
    }

    private static byte lower(byte b) {
        return b >= 'A' && b <= 'Z' ? (byte) (b + 32) : b;
    }
}
