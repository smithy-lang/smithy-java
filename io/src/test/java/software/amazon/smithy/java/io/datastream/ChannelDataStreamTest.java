/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChannelDataStreamTest {

    @Test
    void asChannelReadsFromChannel() throws Exception {
        DataStream dataStream = DataStream.ofChannel(() -> new TrackingChannel(new byte[] {1, 2, 3}));

        ByteBuffer dst = ByteBuffer.allocate(3);

        assertEquals(3, dataStream.asChannel().read(dst));
        assertEquals(ByteBuffer.wrap(new byte[] {1, 2, 3}), dst.flip());
    }

    @Test
    void asChannelConsumesStream() {
        DataStream dataStream = DataStream.ofChannel(() -> new TrackingChannel(new byte[0]));

        dataStream.asChannel();

        assertFalse(dataStream.isAvailable());
        assertThrows(IllegalStateException.class, dataStream::asInputStream);
    }

    @Test
    void asChannelDoesNotCreateInputStream() {
        AtomicInteger streamsCreated = new AtomicInteger();
        AtomicInteger channelsCreated = new AtomicInteger();
        DataStream dataStream = DataStream.ofStreamOrChannel(
                () -> {
                    streamsCreated.incrementAndGet();
                    return new TrackingInputStream(new byte[] {9});
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
        DataStream dataStream = DataStream.ofStreamOrChannel(
                () -> {
                    streamsCreated.incrementAndGet();
                    return new TrackingInputStream(new byte[] {9});
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
    void closeClosesCreatedChannel() {
        var channel = new TrackingChannel(new byte[] {1});
        DataStream dataStream = DataStream.ofChannel(() -> channel);

        dataStream.asChannel();
        dataStream.close();

        assertTrue(channel.closed);
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class TrackingChannel implements ReadableByteChannel {
        private final ByteBuffer data;
        private boolean closed;

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
            return !closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
