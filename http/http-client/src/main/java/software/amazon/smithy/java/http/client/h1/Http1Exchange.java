/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.BoundedInputStream;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.NonClosingOutputStream;

/**
 * HTTP/1.1 exchange implementation.
 * Handles a single request/response over an HTTP/1.1 connection.
 */
public final class Http1Exchange implements HttpExchange {

    private static final int MAX_HEADER_LENGTH = 8192;
    private static final int MAX_HEADER_COUNT = 128;
    private static final int MAX_TOTAL_HEADER_SIZE = 128 * 1024; // 128 KB

    private final Http1Connection connection;
    private final HttpRequest request;

    private OutputStream requestOut;
    private InputStream responseIn;
    private HttpHeaders responseHeaders;
    private int statusCode = -1;
    private boolean requestWritten = false;

    Http1Exchange(Http1Connection connection, HttpRequest request) throws IOException {
        this.connection = connection;
        this.request = request;

        // Write request line and headers immediately
        writeRequestLine();
        writeHeaders(request.headers());
    }

    @Override
    public OutputStream requestBody() {
        if (requestOut == null) {
            OutputStream socketOut = connection.getOutputStream();

            String transferEncoding = request.headers().firstValue("Transfer-Encoding");
            String contentLength = request.headers().firstValue("Content-Length");

            if ("chunked".equalsIgnoreCase(transferEncoding)) {
                // Use chunked encoding
                requestOut = new ChunkedOutputStream(socketOut);
            } else if (contentLength != null) {
                // Fixed length - just wrap to prevent closing socket
                requestOut = new NonClosingOutputStream(socketOut);
            } else {
                // No Content-Length, no Transfer-Encoding
                // For methods that shouldn't have a body (GET, HEAD), this is fine
                // For POST/PUT without length, should use chunked (could auto-add)
                requestOut = new NonClosingOutputStream(socketOut);
            }
        }
        return requestOut;
    }

    @Override
    public InputStream responseBody() throws IOException {
        if (responseIn == null) {
            // Ensure request is complete
            // Note: This enforces HTTP/1.1's sequential request/response model
            // True bidirectional streaming requires HTTP/2
            ensureRequestComplete();

            // Parse response headers if not done
            if (statusCode == -1) {
                parseResponse();
            }

            responseIn = createResponseStream();
        }
        return responseIn;
    }

    @Override
    public boolean supportsBidirectionalStreaming() {
        // HTTP/1.1 does not support true bidirectional streaming
        // Request must complete before response can be read
        return false;
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        if (responseHeaders == null) {
            ensureRequestComplete();
            parseResponse();
        }
        return responseHeaders;
    }

    @Override
    public int statusCode() throws IOException {
        if (statusCode == -1) {
            ensureRequestComplete();
            parseResponse();
        }
        return statusCode;
    }

    @Override
    public void close() throws IOException {
        // Close response stream if opened
        if (responseIn != null) {
            responseIn.close();
        }

        // Close request stream if opened
        if (requestOut != null) {
            requestOut.close();
        }

        // Release exchange permit on connection
        connection.releaseExchange();
    }

    private void writeRequestLine() throws IOException {
        OutputStream out = connection.getOutputStream();

        String path = request.uri().getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        String query = request.uri().getRawQuery();
        if (query != null) {
            path = path + "?" + query;
        }

        String requestLine = request.method() + " " + path + " HTTP/1.1\r\n";
        out.write(requestLine.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeHeaders(HttpHeaders headers) throws IOException {
        OutputStream out = connection.getOutputStream();

        // Ensure Host header is present
        if (headers.firstValue("Host") == null) {
            String host = request.uri().getHost();
            int port = request.uri().getPort();

            if (port != -1 && port != 80 && port != 443) {
                host = host + ":" + port;
            }

            String hostHeader = "Host: " + host + "\r\n";
            out.write(hostHeader.getBytes(StandardCharsets.US_ASCII));
        }

        // Write all headers
        for (String name : headers.map().keySet()) {
            for (String value : headers.allValues(name)) {
                String header = name + ": " + value + "\r\n";
                out.write(header.getBytes(StandardCharsets.US_ASCII));
            }
        }

        // Blank line to end headers
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private void ensureRequestComplete() throws IOException {
        if (!requestWritten) {
            // If user never accessed requestBody(), close it now
            if (requestOut == null) {
                requestBody().close();
            } else if (requestOut instanceof NonClosingOutputStream) {
                requestOut.close();
            } else {
                // ChunkedOutputStream - ensure it's closed
                requestOut.close();
            }
            requestWritten = true;
        }
    }

    private void parseResponse() throws IOException {
        InputStream in = connection.getInputStream();

        try {
            // Read status line: HTTP/1.1 200 OK
            String statusLine = readLine(in);
            String[] parts = statusLine.split(" ", 3);

            if (parts.length < 2) {
                throw new IOException("Invalid HTTP response: " + statusLine);
            }

            this.statusCode = Integer.parseInt(parts[1]);

            // Read headers
            Map<String, List<String>> headers = new HashMap<>();
            String line;
            int headerCount = 0;
            int totalHeaderSize = 0;

            while (!(line = readLine(in)).isEmpty()) {
                headerCount++;
                totalHeaderSize += line.length();

                if (headerCount > MAX_HEADER_COUNT) {
                    throw new IOException("Too many HTTP headers: " + headerCount
                            + " exceeds maximum of " + MAX_HEADER_COUNT);
                }

                if (totalHeaderSize > MAX_TOTAL_HEADER_SIZE) {
                    throw new IOException("Total HTTP header size " + totalHeaderSize
                            + " bytes exceeds maximum of " + MAX_TOTAL_HEADER_SIZE + " bytes");
                }

                int colon = line.indexOf(':');
                if (colon == -1) {
                    throw new IOException("Invalid header line: " + line);
                }

                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                headers.computeIfAbsent(name, n -> new ArrayList<>()).add(value);
            }

            this.responseHeaders = HttpHeaders.of(headers);

            // Check if connection should be kept alive
            // HTTP/1.1 defaults to keep-alive unless Connection: close is present
            String connectionHeader = this.responseHeaders.firstValue("Connection");
            if (connectionHeader != null && connectionHeader.equalsIgnoreCase("close")) {
                connection.setKeepAlive(false);
            }
        } catch (SocketTimeoutException e) {
            // Enhance timeout exception with request context
            throw new SocketTimeoutException("Read timeout while waiting for HTTP response headers from "
                    + request.uri() + " (check read timeout configuration)");
        }
    }

    private InputStream createResponseStream() throws IOException {
        InputStream socketIn = connection.getInputStream();

        // Check for chunked encoding
        String transferEncoding = responseHeaders.firstValue("Transfer-Encoding");
        if (transferEncoding != null && transferEncoding.toLowerCase().contains("chunked")) {
            return new ChunkedInputStream(socketIn);
        }

        // Check for Content-Length
        String contentLength = responseHeaders.firstValue("Content-Length");
        if (contentLength != null) {
            long length = Long.parseLong(contentLength.trim());
            return new BoundedInputStream(socketIn, length);
        }

        // No length indicator
        // For 1xx, 204, 304 responses - no body
        // For 3xx redirects without Content-Length - assume no body (common case)
        if ((statusCode >= 100 && statusCode < 200) ||
                statusCode == 204
                ||
                statusCode == 304
                ||
                (statusCode >= 300 && statusCode < 400)) {
            return new BoundedInputStream(socketIn, 0);
        }

        // Read until connection closes (HTTP/1.0 style)
        // Connection will not be reusable after this
        connection.setKeepAlive(false);
        return socketIn;
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int b;

        try {
            while ((b = in.read()) != -1) {
                if (b == '\r') {
                    int next = in.read();
                    if (next == '\n') {
                        break;
                    }
                    line.append((char) b);
                    if (next != -1) {
                        line.append((char) next);
                    }
                } else if (b == '\n') {
                    break;
                } else {
                    line.append((char) b);
                }

                // Safety limit to prevent unbounded memory use from malicious servers
                if (line.length() > MAX_HEADER_LENGTH) {
                    throw new IOException("HTTP header line exceeds maximum length of "
                            + MAX_HEADER_LENGTH + " characters");
                }
            }
        } catch (SocketTimeoutException e) {
            throw new SocketTimeoutException("Read timeout while reading HTTP response from "
                    + request.uri().getHost() + " (check read timeout configuration)");
        }

        return line.toString();
    }
}
