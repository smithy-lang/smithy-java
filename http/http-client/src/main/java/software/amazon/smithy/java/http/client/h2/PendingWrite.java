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
     * The data buffer (borrowed from ByteAllocator).
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
     * Frame flags for the DATA frame. Valid flags from {@link H2Constants}:
     * <ul>
     *   <li>{@link H2Constants#FLAG_END_STREAM} (0x1) - Last frame for this stream</li>
     *   <li>{@link H2Constants#FLAG_PADDED} (0x8) - Frame is padded (not used)</li>
     * </ul>
     */
    int flags;

    /**
     * Initialize this pending write with data.
     *
     * @param data   the data buffer
     * @param offset offset within buffer
     * @param length length to write
     * @param flags  frame flags (see {@link H2Constants#FLAG_END_STREAM})
     */
    void init(byte[] data, int offset, int length, int flags) {
        this.data = data;
        this.offset = offset;
        this.length = length;
        this.flags = flags;
    }

    /**
     * Reset this instance for reuse.
     */
    void reset() {
        this.data = null;
        this.offset = 0;
        this.length = 0;
        this.flags = 0;
    }
}
