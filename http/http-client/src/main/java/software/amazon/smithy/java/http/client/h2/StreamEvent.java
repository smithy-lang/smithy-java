/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.util.List;
import software.amazon.smithy.java.http.client.h2.hpack.HpackDecoder;

/**
 * Events that can occur on an HTTP/2 stream.
 *
 * <p>This sealed interface represents events that the connection's reader thread
 * can deliver to a stream. Note that DATA is not represented here - DATA frame
 * payloads are written directly into the exchange's buffer for zero-allocation reads.
 */
sealed interface StreamEvent {
    /**
     * Pre-decoded response headers from the reader thread.
     *
     * <p>HPACK decoding happens in the reader thread to ensure dynamic table
     * updates are processed in frame order across all streams on the connection.
     *
     * @param fields the decoded header fields
     * @param endStream true if FLAG_END_STREAM was set on the HEADERS frame
     */
    record Headers(List<HpackDecoder.HeaderField> fields, boolean endStream) implements StreamEvent {}

    /**
     * Per-stream error (RST_STREAM received, protocol error, etc.).
     */
    record StreamError(H2Exception cause) implements StreamEvent {}

    /**
     * Connection-level error (GOAWAY, I/O error, etc.).
     */
    record ConnectionError(IOException cause) implements StreamEvent {}
}
