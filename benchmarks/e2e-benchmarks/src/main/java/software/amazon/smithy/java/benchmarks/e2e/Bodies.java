/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.io.datastream.DataStream;

/** Reads bodies to the end, as a real transport and a real caller would. */
final class Bodies {

    private static final ThreadLocal<DrainingSink> SCRATCH = ThreadLocal.withInitial(DrainingSink::new);

    private Bodies() {}

    /**
     * Keep request and response writeTo call sites separate so HotSpot profiles their body types independently.
     */
    static long drain(DataStream body) {
        if (body == null) {
            return 0;
        }
        DrainingSink sink = SCRATCH.get();
        sink.total = 0;
        try {
            body.writeTo(sink);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sink.total;
    }

    static long drainPayload(DataStream payload) {
        if (payload == null) {
            return 0;
        }
        DrainingSink sink = SCRATCH.get();
        sink.total = 0;
        try {
            payload.writeTo(sink);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sink.total;
    }

    private static final class DrainingSink extends OutputStream {
        private final byte[] scratch = new byte[8192];
        long total;

        @Override
        public void write(int b) {
            scratch[0] = (byte) b;
            total++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            // Copy each byte to model a socket buffer.
            int remaining = len;
            while (remaining > 0) {
                int n = Math.min(remaining, scratch.length);
                System.arraycopy(b, off + (len - remaining), scratch, 0, n);
                remaining -= n;
            }
            total += len;
        }
    }

    /** Reads every byte because deserialization may leave a payload in a buffer without copying it. */
    static void consume(ByteBuffer payload) {
        ByteBuffer b = payload.duplicate();
        byte[] buf = SCRATCH.get().scratch;
        while (b.hasRemaining()) {
            b.get(buf, 0, Math.min(buf.length, b.remaining()));
        }
    }
}
