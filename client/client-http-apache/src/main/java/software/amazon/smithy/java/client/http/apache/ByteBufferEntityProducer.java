/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import software.amazon.smithy.java.io.datastream.DataStream;

final class ByteBufferEntityProducer implements AsyncEntityProducer {
    private final DataStream body;
    private final long contentLength;
    private final String contentType;
    private final ByteBuffer source;
    private boolean endStream;
    private boolean closed;

    ByteBufferEntityProducer(DataStream body) {
        this.body = body;
        this.contentLength = body.contentLength();
        this.contentType = body.contentType();
        this.source = body.asByteBuffer().asReadOnlyBuffer();
    }

    @Override
    public int available() {
        return endStream ? 0 : source.remaining();
    }

    @Override
    public void produce(DataStreamChannel channel) throws IOException {
        if (endStream) {
            channel.endStream(List.of());
            return;
        }

        channel.write(source);
        if (!source.hasRemaining()) {
            endStream = true;
            releaseResources();
            channel.endStream(List.of());
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
        return false;
    }

    @Override
    public Set<String> getTrailerNames() {
        return Set.of();
    }

    @Override
    public boolean isRepeatable() {
        return true;
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
        body.close();
    }
}
