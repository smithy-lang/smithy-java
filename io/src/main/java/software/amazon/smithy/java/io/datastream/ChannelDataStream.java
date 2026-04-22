/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

final class ChannelDataStream implements DataStream {

    private final DataStream.IOSupplier<InputStream> inputStreamSupplier;
    private final DataStream.IOSupplier<ReadableByteChannel> channelSupplier;
    private final String contentType;
    private final long contentLength;
    private final boolean replayable;
    private boolean consumed;
    private boolean closed;
    private AutoCloseable current;

    ChannelDataStream(
            DataStream.IOSupplier<InputStream> inputStreamSupplier,
            DataStream.IOSupplier<ReadableByteChannel> channelSupplier,
            String contentType,
            long contentLength,
            boolean replayable
    ) {
        this.inputStreamSupplier = inputStreamSupplier;
        this.channelSupplier = channelSupplier;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.replayable = replayable;
    }

    @Override
    public InputStream asInputStream() {
        markConsumed();
        try {
            InputStream result = inputStreamSupplier != null
                    ? inputStreamSupplier.get()
                    : Channels.newInputStream(channelSupplier.get());
            current = result;
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ReadableByteChannel asChannel() {
        markConsumed();
        try {
            ReadableByteChannel result = channelSupplier != null
                    ? channelSupplier.get()
                    : Channels.newChannel(inputStreamSupplier.get());
            current = result;
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        asInputStream().transferTo(out);
    }

    @Override
    public void writeTo(WritableByteChannel dst) throws IOException {
        try (ReadableByteChannel src = asChannel()) {
            ByteBuffer buf = ByteBuffer.allocate(8192);
            while (src.read(buf) >= 0) {
                buf.flip();
                while (buf.hasRemaining()) {
                    dst.write(buf);
                }
                buf.clear();
            }
        }
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public boolean isReplayable() {
        return replayable;
    }

    @Override
    public boolean isAvailable() {
        return replayable || !consumed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (current != null) {
                try {
                    current.close();
                } catch (Exception e) {
                    if (e instanceof IOException ioe) {
                        throw new UncheckedIOException("Failed to close data stream", ioe);
                    }
                    throw new RuntimeException("Failed to close data stream", e);
                }
            }
        }
    }

    private void markConsumed() {
        if (!replayable && consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }
        consumed = true;
    }
}
