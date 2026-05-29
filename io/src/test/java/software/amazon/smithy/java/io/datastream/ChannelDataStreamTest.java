/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import org.junit.jupiter.api.Test;

class ChannelDataStreamTest {

    @Test
    void asChannelReadsFromChannel() throws Exception {
        DataStream dataStream = DataStream.ofChannel(new TrackingChannel(new byte[] {1, 2, 3}));

        ByteBuffer dst = ByteBuffer.allocate(3);

        assertEquals(3, dataStream.asChannel().read(dst));
        assertEquals(ByteBuffer.wrap(new byte[] {1, 2, 3}), dst.flip());
    }

    @Test
    void asChannelConsumesStream() {
        DataStream dataStream = DataStream.ofChannel(new TrackingChannel(new byte[0]));

        dataStream.asChannel();

        assertFalse(dataStream.isAvailable());
        assertThrows(IllegalStateException.class, dataStream::asInputStream);
    }

    @Test
    void closeClosesCreatedChannel() {
        var channel = new TrackingChannel(new byte[] {1});
        DataStream dataStream = DataStream.ofChannel(channel);

        dataStream.close();

        assertTrue(channel.closed);
    }

    @Test
    void writeToOutputStreamCopiesFromChannel() throws IOException {
        DataStream dataStream = DataStream.ofChannel(new TrackingChannel(new byte[] {1, 2, 3}));
        var out = new ByteArrayOutputStream();

        dataStream.writeTo(out);

        assertEquals(ByteBuffer.wrap(new byte[] {1, 2, 3}), ByteBuffer.wrap(out.toByteArray()));
    }

    @Test
    void discardDrainsAndClosesChannel() throws IOException {
        var channel = new TrackingChannel(new byte[] {1, 2, 3});
        DataStream dataStream = DataStream.ofChannel(channel);

        dataStream.discard();

        assertFalse(dataStream.isAvailable());
        assertTrue(channel.closed);
        assertFalse(channel.data.hasRemaining());
    }

    @Test
    void discardKnownLengthDrainsOnlyContentLength() throws IOException {
        var channel = new TrackingChannel(new byte[] {1, 2, 3, 4});
        DataStream dataStream = DataStream.ofChannel(channel, null, 2);

        dataStream.discard();

        assertTrue(channel.closed);
        assertEquals(2, channel.data.position());
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
