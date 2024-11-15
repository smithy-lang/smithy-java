/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A wrapper around bytes of data, avoiding state issues with {@link ByteBuffer}.
 *
 * @param bytes  The underlying bytes to wrap. These bytes are not copied and are used as-is.
 * @param offset Starting position of the byte array.
 * @param length Number of bytes to include in the Slice after offset.
 */
public record Slice(byte[] bytes, int offset, int length) {

    public Slice {
        Objects.requireNonNull(bytes);
        if (offset < 0 || offset > bytes.length) {
            throw new IndexOutOfBoundsException("Offset is out of bounds");
        }
        if (length < 0 || offset + length > bytes.length) {
            throw new IndexOutOfBoundsException("Length is out of bounds");
        }
    }

    /**
     * Create a Slice from bytes, assuming all the bytes are part of the Slice.
     *
     * @param bytes Bytes to wrap.
     */
    public Slice(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    /**
     * Create a Slice from a ByteBuffer using the current position of the buffer as the offset.
     *
     * @param byteBuffer Buffer to convert from.
     */
    public Slice(ByteBuffer byteBuffer) {
        this(byteBuffer.array(), byteBuffer.position(), byteBuffer.remaining());
    }

    /**
     * Get a byte from the slice at the given position relative to the zero-position of the Slice.
     *
     * @param pos Position to read from the Slice offset starting at 0.
     * @return the byte.
     * @throws IndexOutOfBoundsException if the position is not within the Slice.
     */
    public byte at(int pos) {
        if (pos < length) {
            return bytes[offset + pos];
        } else {
            throw new IndexOutOfBoundsException("Index out of bounds");
        }
    }

    /**
     * Convert the Slice to a ByteBuffer.
     *
     * @return the created ByteBuffer.
     */
    public ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(bytes, offset, length);
    }

    /**
     * Copy the bytes contained in the slice into a byte array.
     *
     * @return the copied byte array.
     */
    public byte[] copyBytes() {
        byte[] copy = new byte[length];
        System.arraycopy(bytes, offset, copy, 0, length);
        return copy;
    }
}
