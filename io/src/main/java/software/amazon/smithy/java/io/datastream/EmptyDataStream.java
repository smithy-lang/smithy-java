/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

final class EmptyDataStream implements DataStream {
    /**
     * Minimal empty publisher.
     */
    static final Flow.Publisher<ByteBuffer> PUBLISHER = subscriber -> {
        subscriber.onSubscribe(new Flow.Subscription() {
            private final AtomicBoolean done = new AtomicBoolean();

            @Override
            public void request(long n) {
                if (!done.compareAndSet(false, true)) {
                    return;
                }
                if (n <= 0) {
                    subscriber.onError(new IllegalArgumentException("non-positive subscription request"));
                } else {
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                done.set(true);
            }
        });
    };

    static final EmptyDataStream INSTANCE = new EmptyDataStream();
    private static final byte[] EMPTY_BYTES = new byte[0];

    @Override
    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(EMPTY_BYTES);
    }

    @Override
    public boolean hasByteBuffer() {
        return true;
    }

    @Override
    public InputStream asInputStream() {
        return InputStream.nullInputStream();
    }

    @Override
    public void writeTo(OutputStream out) {
        // No-op
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
    public long contentLength() {
        return 0;
    }

    @Override
    public String contentType() {
        return null;
    }

    @Override
    public boolean hasKnownLength() {
        return true;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        PUBLISHER.subscribe(subscriber);
    }
}
