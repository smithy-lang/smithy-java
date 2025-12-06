/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.http.api.HeaderUtils;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;
import software.amazon.smithy.java.http.client.BoundedInputStream;
import software.amazon.smithy.java.http.client.DelegatedClosingInputStream;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.NonClosingOutputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;
import software.amazon.smithy.java.http.client.connection.Route;

/**
 * HTTP/1.1 exchange implementation, handling a single request/response over a connection.
 *
 * <h2>Request/Response Flow</h2>
 * <p>HTTP/1.1 is a sequential protocol: the request must be fully sent before the response can be read. This class
 * enforces this ordering:
 * <ol>
 *   <li>Request line and headers are written on construction</li>
 *   <li>Request body is written via {@link #requestBody()}</li>
 *   <li>Response is read via {@link #responseStatusCode()}, {@link #responseHeaders()}, {@link #responseBody()}</li>
 * </ol>
 *
 * <h2>Expect: 100-continue</h2>
 * <p>When the request includes {@code Expect: 100-continue}, the client:
 * <ol>
 *   <li>Sends headers and waits for 100 Continue response</li>
 *   <li>If server sends 100, proceeds to send body</li>
 *   <li>If server sends 4xx/5xx, skips body transmission</li>
 *   <li>If server doesn't respond within timeout, proceeds anyway</li>
 * </ol>
 *
 * <h2>Proxy Support</h2>
 * <p>When used through a non-tunneled HTTP proxy, requests are formatted with absolute URIs instead of relative paths:
 * <ul>
 *   <li>Direct/tunneled: {@code GET /users HTTP/1.1}</li>
 *   <li>HTTP proxy: {@code GET http://api.example.com/users HTTP/1.1}</li>
 * </ul>
 *
 * <h2>Transfer Encoding</h2>
 * <p>Supports both chunked transfer encoding and fixed Content-Length for request and response bodies.
 */
public final class H1Exchange implements HttpExchange {

    private static final int MAX_RESPONSE_HEADER_COUNT = 512;
    private static final long DEFAULT_CONTINUE_TIMEOUT_MS = 1000; // 1 second

    // Pre-allocated byte arrays for constant HTTP strings
    private static final byte[] HTTP_1_1_CRLF = " HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] COLON_SPACE = ": ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HOST_HEADER = "Host: ".getBytes(StandardCharsets.US_ASCII);

    private final H1Connection connection;
    private final HttpRequest request;
    private final Route route;
    private final byte[] responseLineBuffer; // Reused buffer for header parsing

    private OutputStream requestOut;
    private InputStream responseIn;
    private ChunkedInputStream chunkedResponseIn; // Reference for trailer access
    private HttpHeaders responseHeaders;
    private HttpVersion responseVersion;
    private int statusCode = -1;
    private boolean requestWritten = false;
    private boolean expectContinueHandled = false;
    private boolean closed;

    /**
     * Create a new HTTP/1.1 exchange.
     *
     * <p>Immediately writes request line and headers to the connection.
     *
     * @param connection the HTTP/1.1 connection to use
     * @param request the HTTP request to send
     * @param route the route this connection is for (needed for proxy formatting)
     * @param lineBuffer reusable buffer for reading response header lines
     * @throws IOException if writing request line or headers fails
     */
    H1Exchange(H1Connection connection, HttpRequest request, Route route, byte[] lineBuffer) throws IOException {
        this.connection = connection;
        this.request = request;
        this.route = route;
        this.responseLineBuffer = lineBuffer;

        // Write request line and headers directly to output buffer
        UnsyncBufferedOutputStream out = connection.getOutputStream();
        writeRequestLine(out);
        writeHeaders(out, request.headers());
        out.flush();
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public OutputStream requestBody() {
        if (requestOut == null) {
            OutputStream socketOut = connection.getOutputStream();
            var headers = request.headers();

            // Handle Expect: 100-continue before creating output stream
            String expectHeader = headers.firstValue("expect");
            if (expectHeader != null && expectHeader.equalsIgnoreCase("100-continue")) {
                try {
                    handleExpectContinue();
                } catch (IOException e) {
                    // Wrap exception for later throwing when writing
                    requestOut = new FailingOutputStream(e);
                    return requestOut;
                }
            }

            String transferEncoding = headers.firstValue("transfer-encoding");
            if ("chunked".equalsIgnoreCase(transferEncoding)) {
                // RFC 9110 Section 6.3: Content-Length MUST NOT be sent with Transfer-Encoding
                if (headers.firstValue("content-length") != null) {
                    throw new IllegalArgumentException(
                            "Request cannot have both Content-Length and Transfer-Encoding headers");
                }
                requestOut = new ChunkedOutputStream(socketOut);
            } else {
                requestOut = new NonClosingOutputStream(socketOut);
            }
        }
        return requestOut;
    }

    @Override
    public InputStream responseBody() throws IOException {
        if (responseIn == null) {
            ensureRequestComplete();
            if (statusCode == -1) {
                parseStatusLineAndHeaders();
            }
            // For HTTP/1.1, request is already complete, so close exchange when response closes
            responseIn = new DelegatedClosingInputStream(createResponseStream(), in -> close());
        }
        return responseIn;
    }

    @Override
    public void setRequestTrailers(HttpHeaders trailers) {
        if (!(requestOut instanceof ChunkedOutputStream cos)) {
            throw new IllegalStateException("Request trailers require chunked transfer encoding");
        }
        cos.setTrailers(trailers);
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        if (responseHeaders == null) {
            ensureRequestComplete();
            parseStatusLineAndHeaders();
        }
        return responseHeaders;
    }

    @Override
    public int responseStatusCode() throws IOException {
        if (statusCode == -1) {
            ensureRequestComplete();
            parseStatusLineAndHeaders();
        }
        return statusCode;
    }

    @Override
    public HttpVersion responseVersion() throws IOException {
        if (responseVersion == null) {
            ensureRequestComplete();
            parseStatusLineAndHeaders();
        }
        return responseVersion;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (responseIn != null) {
                responseIn.close();
            }
            if (requestOut != null) {
                requestOut.close();
            }
            connection.releaseExchange();
        }
    }

    /**
     * Get trailer headers from chunked transfer encoding response.
     *
     * <p>Trailers are only available for chunked responses after the entire
     * response body has been read. For non-chunked responses, this returns null.
     *
     * @return trailer headers, or null if no trailers were received
     */
    @Override
    public HttpHeaders responseTrailerHeaders() {
        // Trailers are only available from chunked responses
        if (chunkedResponseIn != null) {
            return chunkedResponseIn.getTrailers();
        }
        return null;
    }

    /**
     * Handle Expect: 100-continue negotiation.
     *
     * <p>Waits up to N seconds for 100 Continue response:
     * <ul>
     *   <li>100 Continue → proceed with body</li>
     *   <li>417 Expectation Failed → throw exception</li>
     *   <li>Other response → parse as final response, skip body</li>
     *   <li>Timeout → proceed with body anyway</li>
     * </ul>
     *
     * @throws IOException if error response received or I/O fails
     */
    private void handleExpectContinue() throws IOException {
        if (expectContinueHandled) {
            return;
        }
        expectContinueHandled = true;

        UnsyncBufferedInputStream in = connection.getInputStream();

        // Set socket timeout for 100-continue response
        int originalTimeout;
        try {
            originalTimeout = connection.getSocketTimeout();
            connection.setSocketTimeout((int) DEFAULT_CONTINUE_TIMEOUT_MS);
        } catch (IOException e) {
            // Can't set timeout - proceed without waiting
            return;
        }

        try {
            // Try to read 100 Continue response
            int lineLen = readLine(in);

            if (lineLen <= 0) {
                // Timeout waiting for response - proceed with body
                return;
            }

            int code = parseStatusLine(responseLineBuffer, lineLen);

            if (code == 100) {
                // 100 Continue received - read and discard headers, proceed with body
                while (readLine(in) > 0) {
                    // Skip header lines until empty line
                }
            } else if (code == 417) {
                // 417 Expectation Failed - server rejected Expect
                throw new IOException("Server rejected Expect: 100-continue with 417 Expectation Failed");
            } else {
                // Server sent final response without 100 Continue
                // Parse as final response, must not send body
                parseStatusAndHeaders(code, in);
                requestWritten = true; // Skip body transmission
                throw new IOException("Server sent final response " + code
                        + " before request body; body must not be written");
            }
        } catch (SocketTimeoutException e) {
            // Timeout waiting for 100 Continue - proceed with body anyway
            // Some servers don't support 100-continue and just ignore it
            return;
        } finally {
            // Restore original timeout
            try {
                connection.setSocketTimeout(originalTimeout);
            } catch (IOException ignored) {}
        }
    }

    private int readLine(UnsyncBufferedInputStream in) throws IOException {
        return in.readLine(responseLineBuffer, H1Connection.RESPONSE_LINE_BUFFER_SIZE);
    }

    private void writeRequestLine(UnsyncBufferedOutputStream out) throws IOException {
        out.writeAscii(request.method());
        out.write(' ');

        URI uri = request.uri();
        if ("CONNECT".equals(request.method())) {
            // CONNECT uses authority-form: host:port
            out.writeAscii(uri.getHost());
            int port = uri.getPort();
            if (port != -1) {
                out.write(':');
                out.writeAscii(Integer.toString(port));
            }
        } else if (isHttpProxyWithoutTunnel()) {
            out.writeAscii(uri.toString());
        } else {
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                out.write('/');
            } else {
                out.writeAscii(path);
            }

            String query = uri.getRawQuery();
            if (query != null && !query.isEmpty()) {
                out.write('?');
                out.writeAscii(query);
            }
        }

        out.write(HTTP_1_1_CRLF);
    }

    /**
     * Check if this request is going through an HTTP proxy without a tunnel.
     *
     * @return true if absolute URI format is needed
     */
    private boolean isHttpProxyWithoutTunnel() {
        return route != null && (route.usesProxy() && !route.isSecure());
    }

    private void writeHeaders(UnsyncBufferedOutputStream out, HttpHeaders headers) throws IOException {
        // Ensure Host header is present
        if (headers.firstValue("host") == null) {
            URI uri = request.uri();
            out.write(HOST_HEADER);
            out.writeAscii(uri.getHost());
            int port = uri.getPort();
            // Include port only if non-default for the scheme
            if (port != -1 && port != defaultPort(uri.getScheme())) {
                out.write(':');
                out.writeAscii(Integer.toString(port));
            }
            out.write(CRLF);
        }

        // Write all headers
        for (var entry : headers) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                out.writeAscii(name);
                out.write(COLON_SPACE);
                out.writeAscii(value);
                out.write(CRLF);
            }
        }

        // Blank line to end headers
        out.write(CRLF);
    }

    private void ensureRequestComplete() throws IOException {
        if (!requestWritten) {
            if (requestOut != null) {
                requestOut.close();
            }
            connection.getOutputStream().flush();
            requestWritten = true;
        }
    }

    private void parseStatusLineAndHeaders() throws IOException {
        // If we already parsed during Expect: 100-continue, return
        if (statusCode != -1) {
            return;
        }

        UnsyncBufferedInputStream in = connection.getInputStream();

        try {
            // Loop to skip 1xx interim responses (RFC 9110 Section 15.2)
            // Examples: 100 Continue, 103 Early Hints
            int code;
            do {
                int lineLen = readLine(in);
                if (lineLen <= 0) {
                    throw new IOException("Empty HTTP response from " + request.uri());
                }
                code = parseStatusLine(responseLineBuffer, lineLen);

                if (code >= 100 && code < 200) {
                    // Skip 1xx interim response headers
                    while (readLine(in) > 0) {
                        // Discard header lines until empty line
                    }
                }
            } while (code >= 100 && code < 200);

            parseStatusAndHeaders(code, in);
        } catch (SocketTimeoutException e) {
            connection.markInactive();
            throw new SocketTimeoutException("Read timeout while waiting for HTTP response headers from "
                    + request.uri() + " (check read timeout configuration)");
        }
    }

    /**
     * Parse status line. Expects "HTTP/1.x NNN ...".
     * Sets responseVersion and returns status code.
     * Also detects HTTP/1.0 and disables keep-alive (HTTP/1.0 defaults to close).
     */
    private int parseStatusLine(byte[] buf, int len) throws IOException {
        // Validate HTTP version - must be "HTTP/1.0" or "HTTP/1.1".
        // Minimum valid status line: "HTTP/1.x NNN" = 12 bytes
        if (len < 12
                || buf[0] != 'H'
                || buf[1] != 'T'
                || buf[2] != 'T'
                || buf[3] != 'P'
                || buf[4] != '/'
                || buf[5] != '1'
                || buf[6] != '.'
                || buf[8] != ' ') {
            throw new IOException("Malformed HTTP response status line: "
                    + new String(buf, 0, len, StandardCharsets.US_ASCII));
        }

        byte minor = buf[7];
        if (minor == '1') {
            responseVersion = HttpVersion.HTTP_1_1;
        } else if (minor == '0') {
            responseVersion = HttpVersion.HTTP_1_0;
            connection.setKeepAlive(false); // HTTP/1.0 defaults to Connection: close
        } else {
            throw new IOException("Unsupported HTTP version: HTTP/1." + (char) minor);
        }

        // Parse 3-digit status code directly from bytes (positions 9, 10, 11)
        byte c1 = buf[9];
        byte c2 = buf[10];
        byte c3 = buf[11];

        if (c1 < '0' || c1 > '9' || c2 < '0' || c2 > '9' || c3 < '0' || c3 > '9') {
            throw new IOException("Invalid status code in HTTP response: "
                    + new String(buf, 0, len, StandardCharsets.US_ASCII));
        }

        return ((c1 - '0') * 100) + ((c2 - '0') * 10) + (c3 - '0');
    }

    private void parseStatusAndHeaders(int code, UnsyncBufferedInputStream in) throws IOException {
        this.statusCode = code;
        if (statusCode < 100 || statusCode > 599) {
            throw new IOException("Invalid HTTP status code: " + statusCode);
        }

        ModifiableHttpHeaders headers = HttpHeaders.ofModifiable();
        int headerCount = 0;
        boolean sawConnectionClose = false;

        int lineLen;
        while ((lineLen = readLine(in)) > 0) {
            headerCount++;
            if (headerCount > MAX_RESPONSE_HEADER_COUNT) {
                throw new IOException("Too many HTTP headers: " + headerCount
                        + " exceeds maximum of " + MAX_RESPONSE_HEADER_COUNT);
            }

            String name = H1Utils.parseHeaderLine(responseLineBuffer, lineLen, headers);
            if (name == null) {
                throw new IOException("Invalid header line: "
                        + new String(responseLineBuffer, 0, lineLen, StandardCharsets.US_ASCII));
            }

            // Check Connection header to determine keep-alive behavior
            // HTTP/1.1 defaults to keep-alive, HTTP/1.0 defaults to close
            if ("connection".equals(name)) {
                String value = headers.firstValue(name);
                if ("close".equalsIgnoreCase(value)) {
                    sawConnectionClose = true;
                } else if ("keep-alive".equalsIgnoreCase(value)) {
                    // Explicit keep-alive (needed for HTTP/1.0)
                    connection.setKeepAlive(true);
                }
            }
        }

        this.responseHeaders = headers;

        if (sawConnectionClose) {
            connection.setKeepAlive(false);
        }
    }

    private InputStream createResponseStream() throws IOException {
        UnsyncBufferedInputStream socketIn = connection.getInputStream();

        String transferEncoding = responseHeaders.firstValue("transfer-encoding");
        if (transferEncoding != null && containsChunked(transferEncoding)) {
            chunkedResponseIn = new ChunkedInputStream(socketIn);
            return chunkedResponseIn;
        }

        String contentLength = responseHeaders.firstValue("content-length");
        if (contentLength != null) {
            try {
                long length = Long.parseLong(contentLength.trim());
                if (length < 0) {
                    throw new IOException("Invalid negative Content-Length: " + length);
                }
                return new BoundedInputStream(socketIn, length);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid Content-Length header: " + contentLength);
            }
        }

        // No body for certain status codes or HEAD response.
        if (noBodyResponseStatus(statusCode) || "HEAD".equalsIgnoreCase(request.method())) {
            return new BoundedInputStream(socketIn, 0);
        }

        // Read until close (HTTP/1.0 style)
        connection.setKeepAlive(false);
        return socketIn;
    }

    /**
     * Fast check for "chunked" token in transfer-encoding value.
     */
    private static boolean containsChunked(String value) {
        int len = value.length();
        if (len < 7) {
            return false;
        }

        // Fast path: exact match
        if (value.equalsIgnoreCase("chunked")) {
            return true;
        }

        // Multi-value (rare): split and check each token
        if (value.indexOf(',') >= 0) {
            for (String token : value.split(",")) {
                // Only allocates a string when needed
                if (HeaderUtils.normalizeValue(token).equals("chunked")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if status code indicates no response body per RFC 9110 Section 6.4.1.
     */
    private static boolean noBodyResponseStatus(int statusCode) {
        return statusCode == 204 || statusCode == 304 || (statusCode >= 100 && statusCode < 200);
    }

    /**
     * Get default port for scheme (80 for http or unknown, 443 for https).
     */
    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
