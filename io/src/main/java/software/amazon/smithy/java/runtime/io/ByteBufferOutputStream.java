/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public final class ByteBufferOutputStream extends OutputStream {
    private static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    private byte[] buf;
    private int count;

    public ByteBufferOutputStream() {
        this(32);
    }

    public ByteBufferOutputStream(int initialLength) {
        this.buf = new byte[initialLength];
    }

    @Override
    public void write(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    @Override
    public void write(int b) {
        ensureCapacity(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    /**
     * Returns a ByteBuffer that wraps this ByteBufferOutputStream's backing buffer.
     *
     * <p>The returned buffer will always {@linkplain ByteBuffer#hasArray() have an accessible backing array}
     * but the data in the buffer is not guaranteed to begin at {@linkplain ByteBuffer#arrayOffset() offset 0}
     * or to span to {@linkplain ByteBuffer#limit() the buffer's limit}. Always interact with the buffer with
     * the following idiom:
     *
     * <pre>{@code
     * int pos = buffer.arrayOffset() + buffer.position();
     * int len = buffer.remaining();
     * doSomethingWithBuffer(buffer.array(), pos, len);
     * }</pre>
     *
     * @return the backing buffer
     */
    public ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(buf, 0, count);
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

    public int size() {
        return count;
    }

    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = buf.length;
        int minGrowth = minCapacity - oldCapacity;
        if (minGrowth > 0) {
            buf = Arrays.copyOf(
                buf,
                newLength(
                    oldCapacity,
                    minGrowth,
                    oldCapacity /* preferred growth */
                )
            );
        }
    }

    private static int newLength(int oldLength, int minGrowth, int prefGrowth) {
        int prefLength = oldLength + Math.max(minGrowth, prefGrowth); // might overflow
        if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH) {
            return prefLength;
        } else {
            // put code cold in a separate method
            return hugeLength(oldLength, minGrowth);
        }
    }

    private static int hugeLength(int oldLength, int minGrowth) {
        int minLength = oldLength + minGrowth;
        if (minLength < 0) { // overflow
            throw new OutOfMemoryError(
                "Required array length " + oldLength + " + " + minGrowth + " is too large"
            );
        } else if (minLength <= SOFT_MAX_ARRAY_LENGTH) {
            return SOFT_MAX_ARRAY_LENGTH;
        } else {
            return minLength;
        }
    }
}
