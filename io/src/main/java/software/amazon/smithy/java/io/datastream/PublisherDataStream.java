/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

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

    @Override
    public boolean isAvailable() {
        return isReplayable || !consumed;
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
        innerSubscribe(new HttpBodySubscriberAdapter<>(subscriber));
        return subscriber.getBody().toCompletableFuture().join();
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        if (!isReplayable && consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }
        consumed = true;

        var error = new AtomicReference<Throwable>();
        var done = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<>() {
            Flow.Subscription subscription;
            boolean cancelled;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer buf) {
                if (cancelled) {
                    return;
                }
                try {
                    if (buf.hasArray()) {
                        out.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
                    } else {
                        byte[] tmp = new byte[buf.remaining()];
                        buf.get(tmp);
                        out.write(tmp);
                    }
                } catch (IOException e) {
                    cancelled = true;
                    error.set(e);
                    subscription.cancel();
                    done.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while writing publisher data", e);
        }

        Throwable t = error.get();
        if (t instanceof IOException ioe) {
            throw ioe;
        } else if (t != null) {
            throw new IOException("Publisher error", t);
        }
    }

    private void innerSubscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        consumed = true;
        publisher.subscribe(subscriber);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (!isReplayable && consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }

        innerSubscribe(subscriber);
    }
}
