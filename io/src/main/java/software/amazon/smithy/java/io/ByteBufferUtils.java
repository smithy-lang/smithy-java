/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ByteBufferUtils {
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

    private ByteBufferUtils() {}

    public static String base64Encode(ByteBuffer buffer) {
        byte[] encoded = base64EncodeToBytes(buffer);
        return new String(encoded, StandardCharsets.ISO_8859_1);
    }

    public static byte[] base64EncodeToBytes(ByteBuffer buffer) {
        return isExact(buffer)
                ? BASE64_ENCODER.encode(buffer.array())
                : BASE64_ENCODER.encode(buffer.duplicate()).array();
    }

    /**
     * Number of bytes {@link #base64EncodeTo} writes for {@code dataLen} bytes of input.
     */
    public static int base64EncodedSize(int dataLen) {
        return ((dataLen + 2) / 3) * 4;
    }

    /**
     * Base64-encodes {@code buffer} into {@code scratch} starting at index 0, returning the number
     * of bytes written. {@code scratch} must be at least {@link #base64EncodedSize} of the buffer's
     * remaining bytes. The buffer's position is not consumed.
     */
    public static int base64EncodeInto(ByteBuffer buffer, byte[] scratch) {
        if (isExact(buffer)) {
            return BASE64_ENCODER.encode(buffer.array(), scratch);
        }
        byte[] encoded = BASE64_ENCODER.encode(buffer.duplicate()).array();
        System.arraycopy(encoded, 0, scratch, 0, encoded.length);
        return encoded.length;
    }

    /**
     * Base64-encodes {@code buffer} into {@code dst} starting at {@code dstOffset}, returning the
     * number of bytes written. The buffer's position is not consumed. {@code scratch} follows the
     * {@link #base64EncodeInto} contract.
     */
    public static int base64EncodeTo(ByteBuffer buffer, byte[] dst, int dstOffset, byte[] scratch) {
        int written = base64EncodeInto(buffer, scratch);
        System.arraycopy(scratch, 0, dst, dstOffset, written);
        return written;
    }

    public static String getUTF8String(ByteBuffer buffer) {
        var bytes = getBytes(buffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static byte[] getBytes(ByteBuffer buffer) {
        if (isExact(buffer)) {
            return buffer.array();
        }
        byte[] bytes = new byte[buffer.remaining()];
        buffer.asReadOnlyBuffer().get(bytes);
        return bytes;
    }

    public static InputStream byteBufferInputStream(ByteBuffer buffer) {
        return new ByteBufferBackedInputStream(buffer);
    }

    private static boolean isExact(ByteBuffer buffer) {
        return buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.remaining() == buffer.array().length;
    }

    // Copied from jackson data-bind. See NOTICE.
    private static final class ByteBufferBackedInputStream extends InputStream {

        private final ByteBuffer b;

        public ByteBufferBackedInputStream(ByteBuffer buf) {
            b = buf;
        }

        @Override
        public int available() {
            return b.remaining();
        }

        @Override
        public int read() {
            return b.hasRemaining() ? (b.get() & 0xFF) : -1;
        }

        @Override
        public int read(byte[] bytes, int off, int len) {
            if (!b.hasRemaining()) {
                return -1;
            }
            len = Math.min(len, b.remaining());
            b.get(bytes, off, len);
            return len;
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            // Skip buffering used in the default implementation.
            int remaining = b.remaining();
            if (remaining > 0 && b.hasArray()) {
                out.write(b.array(), b.arrayOffset() + b.position(), remaining);
                b.position(b.limit());
            }
            return remaining;
        }
    }
}
