/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Set;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import software.amazon.smithy.java.io.datastream.DataStream;

final class DataStreamEntityProducer implements AsyncEntityProducer {
    /**
     * Apache's own classic-over-async bridge uses a small staging buffer.
     * Large in-memory request bodies bypass this producer entirely.
     */
    private static final int BUFFER_SIZE = 2 * 1024;

    private final DataStream body;
    private final long contentLength;
    private final String contentType;
    private ReadableByteChannel channel;
    private ByteBuffer buffer;
    private boolean endStream;
    private boolean closed;

    DataStreamEntityProducer(DataStream body) {
        this.body = body;
        this.contentLength = body.contentLength();
        this.contentType = body.contentType();
    }

    @Override
    public int available() {
        if (endStream) {
            return 0;
        }
        if (buffer != null && buffer.hasRemaining()) {
            return buffer.remaining();
        }
        return 1;
    }

    @Override
    public void produce(DataStreamChannel channel) throws IOException {
        if (endStream) {
            channel.endStream(List.of());
            return;
        }

        if (this.channel == null) {
            this.channel = body.asChannel();
            this.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            this.buffer.limit(0);
        }

        while (true) {
            if (buffer.hasRemaining()) {
                channel.write(buffer);
                if (buffer.hasRemaining()) {
                    return;
                }
            }

            buffer.clear();
            int read = this.channel.read(buffer);
            if (read < 0) {
                endStream = true;
                releaseResources();
                channel.endStream(List.of());
                return;
            }
            if (read == 0) {
                buffer.limit(0);
                channel.requestOutput();
                return;
            }
            buffer.flip();
        }
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public boolean isChunked() {
        return contentLength < 0;
    }

    @Override
    public Set<String> getTrailerNames() {
        return Set.of();
    }

    @Override
    public boolean isRepeatable() {
        return body.isReplayable();
    }

    @Override
    public void failed(Exception cause) {
        releaseResources();
    }

    @Override
    public void releaseResources() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {} finally {
            body.close();
        }
    }
}
