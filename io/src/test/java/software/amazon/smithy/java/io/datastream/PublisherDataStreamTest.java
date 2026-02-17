/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.SubmissionPublisher;
import org.junit.jupiter.api.Test;

class PublisherDataStreamTest {

    @Test
    void writeTo() throws IOException {
        var chunk1 = "hello ".getBytes(StandardCharsets.UTF_8);
        var chunk2 = "world".getBytes(StandardCharsets.UTF_8);

        var publisher = new SubmissionPublisher<ByteBuffer>();
        var ds = DataStream.ofPublisher(publisher, null, -1);
        var out = new ByteArrayOutputStream();

        Thread.startVirtualThread(() -> {
            publisher.submit(ByteBuffer.wrap(chunk1));
            publisher.submit(ByteBuffer.wrap(chunk2));
            publisher.close();
        });

        ds.writeTo(out);

        assertArrayEquals("hello world".getBytes(StandardCharsets.UTF_8), out.toByteArray());
    }

    @Test
    void writeToNotReplayable() throws IOException {
        var publisher = new SubmissionPublisher<ByteBuffer>();
        var ds = DataStream.ofPublisher(publisher, null, -1);

        Thread.startVirtualThread(publisher::close);
        ds.writeTo(new ByteArrayOutputStream());

        assertThrows(IllegalStateException.class, () -> ds.writeTo(new ByteArrayOutputStream()));
    }

    @Test
    void writeToEmpty() throws IOException {
        var publisher = new SubmissionPublisher<ByteBuffer>();
        var ds = DataStream.ofPublisher(publisher, null, 0);
        var out = new ByteArrayOutputStream();

        Thread.startVirtualThread(publisher::close);
        ds.writeTo(out);

        assertEquals(0, out.size());
    }
}
