/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
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
        return ByteBufferUtils.byteBufferInputStream(buffer);
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
}
