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
 * <p>This sealed interface represents all possible events that the connection's
 * reader thread can deliver to a stream. Using a single event queue with typed
 * events simplifies the exchange implementation:
 * <ul>
 *   <li>Single source of truth - one queue instead of multiple queues + side channels</li>
 *   <li>Errors as events - no volatile flags, errors flow through the same queue</li>
 *   <li>Explicit ordering - events arrive in wire order</li>
 *   <li>HPACK safety - headers are pre-decoded by reader thread before becoming events</li>
 * </ul>
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

    /**
     * Data chunk from response DATA frame.
     *
     * <p>Implements {@link StreamEvent} to participate in the unified event queue.
     * This avoids wrapper allocations on the hot data path.
     *
     * <p>Invariants:
     * <ul>
     *   <li>Non-end chunks always have {@code endStream == false}</li>
     *   <li>Only the {@link #END} sentinel or a trailing chunk has {@code endStream == true}</li>
     *   <li>{@code data} is null only for the {@link #END} sentinel</li>
     * </ul>
     */
    record DataChunk(byte[] data, int offset, int length, boolean endStream) implements StreamEvent {
        private static final byte[] EMPTY_BYTES = new byte[0];

        /** Sentinel for end of stream. */
        static final DataChunk END = new DataChunk(null, 0, 0, true);

        /** Singleton empty chunk for empty DATA frames without END_STREAM. */
        static final DataChunk EMPTY = new DataChunk(EMPTY_BYTES, 0, 0, false);

        boolean isEnd() {
            return this == END || endStream;
        }
    }
}
