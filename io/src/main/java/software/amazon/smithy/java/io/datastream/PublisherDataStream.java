/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

final class PublisherDataStream implements DataStream {

    private final Flow.Publisher<ByteBuffer> publisher;
    private final long contentLength;
    private final String contentType;
    private final boolean isReplayable;
    private boolean consumed;

    PublisherDataStream(Flow.Publisher<ByteBuffer> publisher, long contentLength, String contentType, boolean replay) {
        this.publisher = publisher;
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.isReplayable = replay;
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
        return isReplayable;
    }

    // Override to skip needing an intermediate InputStream for this.
    @Override
    public ByteBuffer asByteBuffer() {
        if (!isReplayable && consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }
        consumed = true;

        var subscriber = HttpResponse.BodySubscribers.ofByteArray();
        var delegate = new HttpBodySubscriberAdapter<>(subscriber);
        subscribe(delegate);
        return ByteBuffer.wrap(subscriber.getBody().toCompletableFuture().join());
    }

    @Override
    public InputStream asInputStream() {
        if (!isReplayable && consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }

        consumed = true;
        var subscriber = HttpResponse.BodySubscribers.ofInputStream();
        var delegate = new HttpBodySubscriberAdapter<>(subscriber);
        subscribe(delegate);
        return subscriber.getBody().toCompletableFuture().join();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (!isReplayable && consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }

        consumed = true;
        publisher.subscribe(subscriber);
    }
}
