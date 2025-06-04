/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import software.amazon.smithy.java.core.serde.SerializationException;

sealed interface Sink permits Sink.OutputStreamSink, Sink.ResizingSink, Sink.NullSink {
    void write(byte[] b, int off, int len);

    void write(byte[] b);

    void write(int b);

    void write(ByteBuffer b);

    void writeAscii(String s);

    default ByteBuffer finish() {
        return null;
    }

    final class OutputStreamSink implements Sink {
        private final OutputStream os;

        public OutputStreamSink(OutputStream os) {
            this.os = os;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            try {
                os.write(b, off, len);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void write(byte[] b) {
            try {
                os.write(b);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void write(int b) {
            try {
                os.write(b);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void write(ByteBuffer b) {
            try {
                if (b.hasArray()) {
                    os.write(b.array(), b.arrayOffset() + b.position(), b.remaining());
                } else {
                    copyNonArrayBB(b);
                }
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeAscii(String s) {
            try {
                os.write(s.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        private void copyNonArrayBB(ByteBuffer b) throws Exception {
            b = b.duplicate();
            int rem = b.remaining();
            byte[] copy = new byte[rem];
            b.get(copy);
            os.write(copy);
        }
    }

    final class ResizingSink implements Sink {
        private byte[] bytes = new byte[128];
        private int pos;

        @Override
        public void write(byte[] b, int off, int len) {
            ensureCapacity(len);
            System.arraycopy(b, off, bytes, pos, len);
            pos += len;
        }

        @Override
        public void write(byte[] b) {
            write(b, 0, b.length);
        }

        @Override
        public void write(int b) {
            ensureCapacity(1);
            bytes[pos++] = (byte) b;
        }

        @Override
        public void write(ByteBuffer b) {
            if (b.hasArray()) {
                write(b.array(), b.position() + b.arrayOffset(), b.limit());
            } else {
                copyNonArrayBB(b);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void writeAscii(String s) {
            int len = s.length();
            ensureCapacity(len);
            s.getBytes(0, s.length(), bytes, pos);
            pos += len;
        }

        private void copyNonArrayBB(ByteBuffer b) {
            int rem = b.remaining();
            ensureCapacity(rem);
            b.duplicate().get(bytes, pos, rem);
            pos += rem;
        }

        @Override
        public ByteBuffer finish() {
            return ByteBuffer.wrap(bytes, 0, pos);
        }

        private void ensureCapacity(int len) {
            int cap = bytes.length;
            int required = pos + len;
            if (required > cap) {
                bytes = Arrays.copyOf(bytes, Math.max(required, cap + (cap >> 1)));
            }
        }
    }

    final class NullSink implements Sink {
        @Override
        public void write(byte[] b, int off, int len) {

        }

        @Override
        public void write(byte[] b) {

        }

        @Override
        public void write(int b) {

        }

        @Override
        public void write(ByteBuffer b) {

        }

        @Override
        public void writeAscii(String s) {

        }

        @Override
        public ByteBuffer finish() {
            return ByteBuffer.wrap(new byte[0]);
        }
    }
}
