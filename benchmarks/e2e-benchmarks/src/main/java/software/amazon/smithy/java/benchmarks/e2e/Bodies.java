/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.io.datastream.DataStream;

/** Reads bodies to the end, as a real transport and a real caller would. */
final class Bodies {

    private static final ThreadLocal<byte[]> SCRATCH = ThreadLocal.withInitial(() -> new byte[8192]);

    private Bodies() {}

    /** Returns the number of bytes the body held. */
    static long drain(DataStream body) {
        if (body == null) {
            return 0;
        }
        byte[] buf = SCRATCH.get();
        long total = 0;
        try (var in = body.asInputStream()) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return total;
    }

    /** Reads every byte because deserialization may leave a payload in a buffer without copying it. */
    static void consume(ByteBuffer payload) {
        ByteBuffer b = payload.duplicate();
        byte[] buf = SCRATCH.get();
        while (b.hasRemaining()) {
            b.get(buf, 0, Math.min(buf.length, b.remaining()));
        }
    }
}
