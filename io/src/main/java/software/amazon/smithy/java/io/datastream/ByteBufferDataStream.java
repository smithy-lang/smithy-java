/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.io.ByteBufferUtils;

final class ByteBufferDataStream implements DataStream {

    private final ByteBuffer buffer;
    private final String contentType;
    private final long contentLength;

    ByteBufferDataStream(ByteBuffer buffer, String contentType) {
        if (!buffer.hasArray()) {
            throw new IllegalArgumentException("Only ByteBuffers with an accessible byte array are supported");
        }
        this.buffer = buffer;
        this.contentLength = buffer.remaining();
        this.contentType = contentType;
    }

    @Override
    public boolean isReplayable() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return buffer.duplicate();
    }

    @Override
    public InputStream asInputStream() {
        // Use duplicate() to avoid mutating the original buffer's position,
        // allowing the DataStream to be replayed (isReplayable() returns true)
        return ByteBufferUtils.byteBufferInputStream(buffer.duplicate());
    }

    @Override
    public ReadableByteChannel asChannel() {
        return new ByteBufferChannel(buffer.duplicate());
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
    }

    @Override
    public void writeTo(WritableByteChannel channel) throws IOException {
        ByteBuffer duplicate = buffer.duplicate();
        while (duplicate.hasRemaining()) {
            channel.write(duplicate);
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
    public boolean hasKnownLength() {
        return true;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        HttpRequest.BodyPublishers
                .ofByteArray(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
                .subscribe(subscriber);
    }

    private static final class ByteBufferChannel implements ReadableByteChannel {
        private final ByteBuffer source;
        private boolean open = true;

        private ByteBufferChannel(ByteBuffer source) {
            this.source = source;
        }

        @Override
        public int read(ByteBuffer dst) {
            if (!open) {
                return -1;
            }
            if (!source.hasRemaining()) {
                return -1;
            }
            int toCopy = Math.min(source.remaining(), dst.remaining());
            int oldLimit = source.limit();
            source.limit(source.position() + toCopy);
            dst.put(source);
            source.limit(oldLimit);
            return toCopy;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
