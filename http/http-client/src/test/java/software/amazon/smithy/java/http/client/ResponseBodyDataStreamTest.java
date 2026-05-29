/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResponseBodyDataStreamTest {
    @Test
    void asChannelDoesNotCreateInputStream() {
        AtomicInteger streamsCreated = new AtomicInteger();
        AtomicInteger channelsCreated = new AtomicInteger();
        var dataStream = new ResponseBodyDataStream(
                () -> {
                    streamsCreated.incrementAndGet();
                    return new ByteArrayInputStream(new byte[] {9});
                },
                () -> {
                    channelsCreated.incrementAndGet();
                    return new TrackingChannel(new byte[] {1});
                },
                null,
                -1);

        dataStream.asChannel();

        assertEquals(0, streamsCreated.get());
        assertEquals(1, channelsCreated.get());
    }

    @Test
    void asInputStreamDoesNotCreateChannel() {
        AtomicInteger streamsCreated = new AtomicInteger();
        AtomicInteger channelsCreated = new AtomicInteger();
        var dataStream = new ResponseBodyDataStream(
                () -> {
                    streamsCreated.incrementAndGet();
                    return new ByteArrayInputStream(new byte[] {9});
                },
                () -> {
                    channelsCreated.incrementAndGet();
                    return new TrackingChannel(new byte[] {1});
                },
                null,
                -1);

        dataStream.asInputStream();

        assertEquals(1, streamsCreated.get());
        assertEquals(0, channelsCreated.get());
    }

    @Test
    void writeToWritableByteChannelUsesChannelView() throws IOException {
        AtomicInteger streamsCreated = new AtomicInteger();
        AtomicInteger channelsCreated = new AtomicInteger();
        var dataStream = new ResponseBodyDataStream(
                () -> {
                    streamsCreated.incrementAndGet();
                    return new ByteArrayInputStream(new byte[] {9});
                },
                () -> {
                    channelsCreated.incrementAndGet();
                    return new TrackingChannel(new byte[] {1, 2, 3});
                },
                null,
                -1);
        var out = new ByteArrayOutputStream();

        dataStream.writeTo(Channels.newChannel(out));

        assertEquals(0, streamsCreated.get());
        assertEquals(1, channelsCreated.get());
        assertEquals(ByteBuffer.wrap(new byte[] {1, 2, 3}), ByteBuffer.wrap(out.toByteArray()));
    }

    private static final class TrackingChannel implements ReadableByteChannel {
        private final ByteBuffer data;

        TrackingChannel(byte[] bytes) {
            data = ByteBuffer.wrap(bytes);
        }

        @Override
        public int read(ByteBuffer dst) {
            if (!data.hasRemaining()) {
                return -1;
            }
            int toCopy = Math.min(data.remaining(), dst.remaining());
            int oldLimit = data.limit();
            data.limit(data.position() + toCopy);
            dst.put(data);
            data.limit(oldLimit);
            return toCopy;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() throws IOException {}
    }
}
