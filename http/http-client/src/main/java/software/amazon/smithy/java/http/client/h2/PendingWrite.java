/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

/**
 * A pending DATA frame write queued for the writer thread.
 */
final class PendingWrite {
    /**
     * The data buffer (borrowed from BufferPool).
     */
    byte[] data;

    /**
     * Offset within the data buffer.
     */
    int offset;

    /**
     * Length of data to write.
     */
    int length;

    /**
     * Whether this write has the END_STREAM flag.
     */
    boolean endStream;

    /**
     * Frame flags (e.g., END_STREAM).
     */
    int flags;

    /**
     * Initialize this pending write with data.
     *
     * @param data      the data buffer
     * @param offset    offset within buffer
     * @param length    length to write
     * @param endStream whether this is the last write
     * @param flags     frame flags
     */
    void init(byte[] data, int offset, int length, boolean endStream, int flags) {
        this.data = data;
        this.offset = offset;
        this.length = length;
        this.endStream = endStream;
        this.flags = flags;
    }

    /**
     * Reset this instance for reuse.
     */
    void reset() {
        this.data = null;
        this.offset = 0;
        this.length = 0;
        this.endStream = false;
        this.flags = 0;
    }
}
