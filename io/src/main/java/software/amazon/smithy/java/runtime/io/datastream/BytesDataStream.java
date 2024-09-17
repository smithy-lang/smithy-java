/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.datastream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class BytesDataStream implements DataStream {

    private final byte[] bytes;
    private final int head;
    private final int tail;
    private final String contentType;
    private final long contentLength;
    private Flow.Publisher<ByteBuffer> publisher;

    BytesDataStream(byte[] bytes, int head, int tail, String contentType) {
        this.bytes = bytes;
        this.head = head;
        this.tail = tail;
        this.contentType = contentType;
        this.contentLength = tail - head;
    }

    @Override
    public CompletionStage<ByteBuffer> asByteBuffer() {
        return CompletableFuture.completedFuture(ByteBuffer.wrap(bytes));
    }

    @Override
    public CompletionStage<byte[]> asBytes() {
        return CompletableFuture.completedFuture(bytes);
    }

    @Override
    public CompletionStage<InputStream> asInputStream() {
        return CompletableFuture.completedFuture(new ByteArrayInputStream(bytes, head, tail));
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
            publisher = p = HttpRequest.BodyPublishers.ofByteArray(bytes, head, tail);
        }
        p.subscribe(subscriber);
    }
}
