/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.io.ByteBufferUtils;

final class ByteBufferDataStream implements DataStream {

    private final ByteBuffer buffer;
    private final String contentType;
    private final long contentLength;

    ByteBufferDataStream(ByteBuffer buffer, String contentType) {
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
    public boolean hasByteBuffer() {
        return true;
    }

    @Override
    public InputStream asInputStream() {
        // Use duplicate() to avoid mutating the original buffer's position,
        // allowing the DataStream to be replayed (isReplayable() returns true)
        return ByteBufferUtils.byteBufferInputStream(buffer.duplicate());
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        if (buffer.hasArray()) {
            out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        } else {
            var dup = buffer.duplicate();
            var tmp = new byte[dup.remaining()];
            dup.get(tmp);
            out.write(tmp);
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
        if (contentLength == 0) {
            EmptyDataStream.PUBLISHER.subscribe(subscriber);
        } else {
            subscriber.onSubscribe(new Subscription(subscriber));
        }
    }

    // Zero-copy byte-buffer subscription.
    private final class Subscription implements Flow.Subscription {
        private final AtomicBoolean completed = new AtomicBoolean();
        private final Flow.Subscriber<? super ByteBuffer> subscriber;

        Subscription(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0 || !completed.compareAndSet(false, true)) {
                return;
            }
            subscriber.onNext(buffer.duplicate());
            subscriber.onComplete();
        }

        @Override
        public void cancel() {
            completed.set(true);
        }
    }
}
