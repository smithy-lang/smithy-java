/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import software.amazon.smithy.java.http.api.HttpHeaders;

/**
 * HTTP request/response exchange.
 *
 * <p><b>Protocol-Specific Behavior:</b>
 * <ul>
 *   <li><b>HTTP/1.1:</b> Sequential only. Request body must be fully written and closed
 *       before response can be read. True bidirectional streaming is NOT supported.</li>
 *   <li><b>HTTP/2:</b> Full bidirectional streaming. Can read response while writing request.</li>
 * </ul>
 *
 * <p><b>Usage Pattern for HTTP/1.1:</b>
 * <pre>{@code
 * try (OutputStream out = exchange.requestBody()) {
 *     out.write(data);
 * } // Must close request before reading response
 *
 * try (InputStream in = exchange.responseBody()) {
 *     // Now read response
 * }
 * }</pre>
 *
 * <p><b>Usage Pattern for HTTP/2 (bidirectional):</b>
 * <pre>{@code
 * // Can interleave writing and reading
 * OutputStream out = exchange.requestBody();
 * InputStream in = exchange.responseBody();
 * // Write and read concurrently in separate threads
 * }</pre>
 */
public interface HttpExchange extends AutoCloseable {
    /**
     * Write to request body. Blocks on flow control.
     */
    OutputStream requestBody();

    /**
     * Read from response body. Blocks until data available.
     *
     * <p><b>IMPORTANT:</b> On HTTP/1.1, this will block until the request body
     * is fully written and closed. True bidirectional streaming requires HTTP/2.
     */
    InputStream responseBody() throws IOException;

    /**
     * Response headers. Blocks until received.
     *
     * <p><b>IMPORTANT:</b> On HTTP/1.1, this will block until the request body
     * is fully written and closed.
     */
    HttpHeaders responseHeaders() throws IOException;

    /**
     * Response status code. Blocks until received.
     *
     * <p><b>IMPORTANT:</b> On HTTP/1.1, this will block until the request body
     * is fully written and closed.
     */
    int statusCode() throws IOException;

    @Override
    void close() throws IOException;

    /**
     * Check if this exchange supports true bidirectional streaming.
     * Returns true for HTTP/2, false for HTTP/1.1.
     *
     * <p>If false, the request body must be fully written and closed before
     * attempting to read the response.
     */
    default boolean supportsBidirectionalStreaming() {
        return false; // Default to false (HTTP/1.1 behavior)
    }
}
