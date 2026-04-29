/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * {@link InputStream} facade over a {@link JavaHttpClientStreamingDataStream}, exposed to
 * callers via {@link software.amazon.smithy.java.io.datastream.DataStream#asInputStream()}.
 *
 * <p>The class is intentionally small: buffering, synchronization, and producer/consumer
 * coordination all live on the backing {@link JavaHttpClientStreamingDataStream}. This class
 * adds only things that belong on the {@code InputStream} API surface:
 * <ul>
 *   <li>{@link #transferTo(OutputStream)} — writes chunks straight from the underlying
 *       {@link java.nio.ByteBuffer}s to the target {@code OutputStream}, releasing the
 *       internal lock while {@code write} runs. Allocates a scratch byte[] lazily, only if a
 *       non-array-backed chunk is encountered.</li>
 *   <li>{@link #readAllBytes()} and {@link #readNBytes(int)} — when content-length is known,
 *       allocate the exact-sized result array once rather than going through {@link
 *       InputStream}'s default growable implementation.</li>
 *   <li>{@link #available()} and {@link #skip(long)} — delegate to cheap operations on the
 *       backing stream.</li>
 * </ul>
 *
 * <p>{@link #bytesRead} is maintained across every consumption path so that content-length-
 * aware allocations in {@code readAllBytes} / {@code readNBytes} stay correct if the caller
 * mixes read forms (e.g., reads some bytes, then calls {@code readAllBytes}).
 */
final class JavaHttpClientStreamingInputStream extends InputStream {

    /**
     * Size of the reusable scratch buffer used by {@link #transferTo(OutputStream)} when chunks
     * are not array-backed.
     *
     * <p>Benchmark-only sweeps can override this with
     * {@code -Dsmithy.java.client.http.jdk.transferScratchSize=<bytes>} to test whether larger
     * response-drain granularity reduces JDK HttpClient scheduler churn on large streaming bodies.
     */
    private static final int TRANSFER_SCRATCH_SIZE =
            Math.max(1024, Integer.getInteger("smithy.java.client.http.jdk.transferScratchSize", 16 * 1024));

    private final JavaHttpClientStreamingDataStream stream;

    /** Number of bytes returned to the caller so far. Used by {@link #readAllBytes()} when content-length is known. */
    private long bytesRead;

    JavaHttpClientStreamingInputStream(JavaHttpClientStreamingDataStream stream) {
        this.stream = stream;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n < 0 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }
        int n = stream.readInto(b, off, len);
        if (n > 0) {
            bytesRead += n;
        }
        return n;
    }

    @Override
    public int available() {
        return stream.availableBytes();
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        long skipped = stream.skipBytes(n);
        bytesRead += skipped;
        return skipped;
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out");
        byte[] scratch = null;
        long transferred = 0;
        while (true) {
            // Block for the next chunk; writeNextChunkTo holds the internal lock only while
            // dequeuing, then writes to `out` without any lock held.
            int written = stream.writeNextChunkTo(out, scratch);
            if (written == JavaHttpClientStreamingDataStream.SCRATCH_NEEDED) {
                // Stream has a non-array-backed chunk ready; allocate a scratch buffer once
                // and retry. The chunk stays at the head of the queue.
                scratch = new byte[TRANSFER_SCRATCH_SIZE];
                continue;
            }
            if (written < 0) {
                return transferred;
            }
            transferred += written;
            bytesRead += written;
        }
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        long contentLength = stream.contentLength();
        if (contentLength < 0) {
            // Unknown length: fall back to the default growable path.
            return super.readAllBytes();
        }
        long remaining = contentLength - bytesRead;
        if (remaining <= 0) {
            return new byte[0];
        }
        if (remaining > Integer.MAX_VALUE - 8) {
            // Defensive: too large to allocate as a single array.
            return super.readAllBytes();
        }
        byte[] out = new byte[(int) remaining];
        int off = 0;
        while (off < out.length) {
            int n = stream.readInto(out, off, out.length - off);
            if (n < 0) {
                // Server closed early relative to the advertised content-length. Return what
                // we got rather than a mostly-empty oversized array.
                byte[] shorter = new byte[off];
                System.arraycopy(out, 0, shorter, 0, off);
                bytesRead += off;
                return shorter;
            }
            off += n;
        }
        bytesRead += out.length;
        return out;
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        if (len < 0) {
            throw new IllegalArgumentException("len < 0");
        }
        if (len == 0) {
            return new byte[0];
        }
        long contentLength = stream.contentLength();
        // If content-length is known, we can cap the target at the remaining bytes and avoid
        // the super implementation's growable ArrayList-of-chunks allocation.
        if (contentLength >= 0) {
            long remaining = contentLength - bytesRead;
            if (remaining <= 0) {
                return new byte[0];
            }
            int target = (int) Math.min((long) len, remaining);
            byte[] out = new byte[target];
            int off = 0;
            while (off < out.length) {
                int n = stream.readInto(out, off, out.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            if (off < out.length) {
                byte[] shorter = new byte[off];
                System.arraycopy(out, 0, shorter, 0, off);
                bytesRead += off;
                return shorter;
            }
            bytesRead += off;
            return out;
        }
        return super.readNBytes(len);
    }

    @Override
    public void close() {
        stream.close();
    }
}
