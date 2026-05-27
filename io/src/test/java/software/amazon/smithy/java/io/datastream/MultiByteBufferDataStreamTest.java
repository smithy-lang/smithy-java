/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MultiByteBufferDataStreamTest {

    @Test
    public void writeToHeapBuffers() throws IOException {
        // hasArray() == true for heap buffers, exercising the array-fast path.
        var b1 = ByteBuffer.wrap("hello ".getBytes(StandardCharsets.UTF_8));
        var b2 = ByteBuffer.wrap("world".getBytes(StandardCharsets.UTF_8));
        var ds = DataStream.ofByteBuffers(List.of(b1, b2), 11, "text/plain");
        var out = new ByteArrayOutputStream();

        ds.writeTo(out);

        assertArrayEquals("hello world".getBytes(StandardCharsets.UTF_8), out.toByteArray());
    }

    @Test
    public void writeToReadOnlyBuffersUsesScratch() throws IOException {
        // asReadOnlyBuffer().hasArray() returns false (HeapByteBufferR) — exercises the scratch
        // copy path. This is the case that motivated the optimization: the JDK HttpClient produces
        // read-only heap buffers via ZeroCopyBodySubscriber, and writeTo previously allocated a new
        // byte[] per chunk. Verify the bytes still come out correctly.
        var b1 = ByteBuffer.wrap("read-only ".getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer();
        var b2 = ByteBuffer.wrap("path".getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer();
        assertThat(b1.hasArray(), is(false));
        var ds = DataStream.ofByteBuffers(List.of(b1, b2), 14, "text/plain");
        var out = new ByteArrayOutputStream();

        ds.writeTo(out);

        assertArrayEquals("read-only path".getBytes(StandardCharsets.UTF_8), out.toByteArray());
    }

    @Test
    public void writeToReadOnlyBuffersDoesNotMutatePosition() throws IOException {
        // Defends the contract: replayable means writeTo is safe to call again. The previous
        // implementation called dup.get(tmp) which advanced the duplicate's position, but the
        // current implementation must use a duplicate so the original buffer's position survives.
        var b1 = ByteBuffer.wrap("a".getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer();
        var b2 = ByteBuffer.wrap("b".getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer();
        var ds = DataStream.ofByteBuffers(List.of(b1, b2), 2, "text/plain");

        var out1 = new ByteArrayOutputStream();
        ds.writeTo(out1);
        var out2 = new ByteArrayOutputStream();
        ds.writeTo(out2);

        assertArrayEquals("ab".getBytes(StandardCharsets.UTF_8), out1.toByteArray());
        assertArrayEquals("ab".getBytes(StandardCharsets.UTF_8), out2.toByteArray());
    }

    @Test
    public void writeToBufferLargerThanScratch() throws IOException {
        // Drive the inner copy loop past one scratch fill (scratch is 16 KiB internally).
        byte[] payload = new byte[40_000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0xff);
        }
        var b1 = ByteBuffer.wrap(payload, 0, 25_000).asReadOnlyBuffer();
        var b2 = ByteBuffer.wrap(payload, 25_000, 15_000).asReadOnlyBuffer();
        var ds = DataStream.ofByteBuffers(List.of(b1, b2), payload.length, "application/octet-stream");
        var out = new ByteArrayOutputStream();

        ds.writeTo(out);

        assertArrayEquals(payload, out.toByteArray());
    }

    @Test
    public void writeToMixedBuffers() throws IOException {
        // First buffer hits the heap-array fast path, second buffer hits the scratch copy path.
        // Validates the scratch is allocated lazily on the first non-array buffer.
        var b1 = ByteBuffer.wrap("heap ".getBytes(StandardCharsets.UTF_8));
        var b2 = ByteBuffer.wrap("readonly".getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer();
        var ds = DataStream.ofByteBuffers(List.of(b1, b2), 13, "text/plain");
        var out = new ByteArrayOutputStream();

        ds.writeTo(out);

        assertArrayEquals("heap readonly".getBytes(StandardCharsets.UTF_8), out.toByteArray());
    }

    @Test
    public void asByteBufferStitchesAcrossChunks() {
        var b1 = ByteBuffer.wrap("aa".getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer();
        var b2 = ByteBuffer.wrap("bb".getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer();
        var ds = DataStream.ofByteBuffers(List.of(b1, b2), 4, "text/plain");

        assertThat(ds.asByteBuffer(), equalTo(ByteBuffer.wrap("aabb".getBytes(StandardCharsets.UTF_8))));
    }
}
