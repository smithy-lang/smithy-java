/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * HTTP request/response exchange.
 *
 * <p><b>Lifecycle:</b>
 * The exchange automatically closes when both the request and response streams are closed.
 * Using try-with-resources on the exchange is recommended as a safety net, but not strictly required if both streams
 * are properly closed. The {@link #close()} method of an HttpExchange implementation MUST be idempotent and ignore
 * successive calls to close().
 *
 * <p><b>Protocol-Specific Behavior:</b>
 * <ul>
 *   <li><b>HTTP/1.1:</b> Sequential only. Request body must be fully written and closed before response can be read.
 *       True bidirectional streaming is NOT supported. Not thread-safe.</li>
 *   <li><b>HTTP/2:</b> Full bidirectional streaming. Can read response while writing request.
 *       Thread-safe for concurrent read/write from separate threads.</li>
 * </ul>
 *
 * <p><b>Usage Pattern with try-with-resources (recommended):</b>
 * {@snippet :
 * try (HttpExchange exchange = client.newExchange(request)) {
 *     try (OutputStream out = exchange.requestBody()) {
 *         out.write(data);
 *     }
 *     int status = exchange.responseStatusCode();
 *     try (InputStream in = exchange.responseBody()) {
 *         byte[] body = in.readAllBytes();
 *     }
 * }
 * }
 *
 * <p><b>Usage Pattern for hand-off (streams managed separately):</b>
 * {@snippet :
 * // Exchange auto-closes when BOTH streams are closed
 * HttpExchange exchange = client.newExchange(request);
 * // Hand off to different parts of the application
 * sendToWriter(exchange.requestBody());   // Writer closes when done
 * sendToReader(exchange.responseBody());  // Reader closes when done
 * }
 */
public interface HttpExchange extends AutoCloseable {
    /**
     * Create a new buffered HTTP exchange where the response is already available and request does not need to
     * be sent.
     *
     * @param request Request that was sent or that was intercepted.
     * @param response Response to return.
     * @return the buffered HttpExchange.
     */
    static HttpExchange newBufferedExchange(HttpRequest request, HttpResponse response) {
        return new BufferedHttpExchange(request, response);
    }

    /**
     * Returns the HTTP request associated with this exchange.
     *
     * <p>For exchanges created by {@link HttpClient}, this returns the request after
     * interceptors have been applied (the "effective" request).
     *
     * @return the HTTP request
     */
    HttpRequest request();

    /**
     * Where to write the request body. Blocks on flow control.
     *
     * <p>Closing this stream signals the end of the request body. For HTTP/2, closing this stream while the response
     * stream is also closed will automatically close the exchange.
     *
     * @return request body stream
     */
    OutputStream requestBody();

    /**
     * HTTP version from response. Blocks until received.
     *
     * <p>For HTTP/1.x connections, this returns the version from the response
     * status line (HTTP/1.0 or HTTP/1.1). For HTTP/2, always returns HTTP/2.
     *
     * @return HTTP response version
     */
    HttpVersion responseVersion() throws IOException;

    /**
     * Response status code. Blocks until received.
     *
     * <p><b>IMPORTANT:</b> On HTTP/1.1, this will block until the request body
     * is fully written and closed.
     *
     * @return response status code
     */
    int responseStatusCode() throws IOException;

    /**
     * Read from response body. Blocks until data available.
     *
     * <p><b>IMPORTANT:</b> On HTTP/1.1, this will block until the request body
     * is fully written and closed. True bidirectional streaming requires HTTP/2.
     *
     * <p>Closing this stream will automatically close the exchange for HTTP/1.1. For HTTP/2, closing this stream
     * while the request stream is also closed will automatically close the exchange.
     *
     * @return the response input stream to read.
     */
    InputStream responseBody() throws IOException;

    /**
     * Response headers. Blocks until received.
     *
     * <p><b>IMPORTANT:</b> On HTTP/1.1, this will block until the request body
     * is fully written and closed.
     *
     * @return HTTP response headers.
     */
    HttpHeaders responseHeaders() throws IOException;

    /**
     * Get trailer headers if any were received.
     *
     * <p>Trailers are headers sent after the message body. They are supported in:
     * <ul>
     *   <li><b>HTTP/1.1:</b> Via chunked transfer encoding (RFC 7230 Section 4.1.2)</li>
     *   <li><b>HTTP/2:</b> Via HEADERS frame after DATA with END_STREAM (RFC 9113 Section 8.1)</li>
     * </ul>
     *
     * <p><b>IMPORTANT:</b> Trailers are only available after the entire response body
     * has been read. Calling this before the body is fully consumed returns null.
     *
     * @return trailer headers, or null if no trailers were received
     */
    default HttpHeaders responseTrailerHeaders() {
        return null;
    }

    /**
     * Check if this exchange supports true bidirectional streaming.
     * Returns true for HTTP/2, false for HTTP/1.1.
     *
     * <p>If false, the request body must be fully written and closed before
     * attempting to read the response.
     *
     * @return true if the exchange supports bidirectional streaming.
     */
    default boolean supportsBidirectionalStreaming() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method is idempotent and may be called multiple times safely.
     * Subsequent calls after the first have no effect.
     */
    @Override
    void close() throws IOException;
}
