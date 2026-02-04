/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ByteBufferUtils {

    private ByteBufferUtils() {}

    public static String base64Encode(ByteBuffer buffer) {
        byte[] bytes;
        if (isExact(buffer)) {
            bytes = buffer.array();
        } else {
            bytes = new byte[buffer.remaining()];
            buffer.asReadOnlyBuffer().get(bytes);
        }
        return Base64.getEncoder().encodeToString(bytes);
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
    }
}
