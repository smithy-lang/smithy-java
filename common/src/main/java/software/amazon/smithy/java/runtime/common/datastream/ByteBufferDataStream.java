/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.common.datastream;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.runtime.common.ByteBufferUtils;

final class ByteBufferDataStream implements DataStream {

    private final ByteBuffer buffer;
    private final String contentType;
    private final long contentLength;
    private Flow.Publisher<ByteBuffer> publisher;

    ByteBufferDataStream(ByteBuffer buffer, String contentType) {
        this.buffer = buffer;
        this.contentLength = buffer.remaining();
        this.contentType = contentType;
    }

    @Override
    public CompletionStage<ByteBuffer> asByteBuffer() {
        return CompletableFuture.completedFuture(buffer.duplicate());
    }

    @Override
    public CompletionStage<byte[]> asBytes() {
        return CompletableFuture.completedFuture(ByteBufferUtils.getBytes(buffer));
    }

    @Override
    public CompletionStage<InputStream> asInputStream() {
        return CompletableFuture.completedFuture(ByteBufferUtils.byteBufferInputStream(buffer));
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
        var p = publisher;
        if (p == null) {
            publisher = p = HttpRequest.BodyPublishers.ofByteArray(ByteBufferUtils.getBytes(buffer));
        }
        p.subscribe(subscriber);
    }
}
