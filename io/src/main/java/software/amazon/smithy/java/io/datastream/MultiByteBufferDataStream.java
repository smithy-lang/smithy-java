/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.io.ByteBufferUtils;

/**
 * Replayable DataStream backed by two or more {@link ByteBuffer}s.
 *
 * <p>Useful for HTTP response handlers that aggregate chunks delivered by the wire without stitching them into a
 * single contiguous buffer. {@link #asByteBuffer()} stitches lazily and caches the result; {@link #subscribe} emits
 * the chunks downstream with no copies.
 *
 * <p>This class assumes its caller has already short-circuited the empty and single-buffer cases through
 * {@link DataStream#ofByteBuffers(List, long, String)} (which routes those to {@link EmptyDataStream} and
 * {@link ByteBufferDataStream} respectively).
 *
 * <p>Ownership: the supplied list is taken by reference, not defensively copied. Callers MUST NOT mutate the list or
 * the buffers' positions/limits after handing them off. The HTTP subscriber path satisfies this trivially: the JDK
 * BodySubscriber contract guarantees the JDK will not touch buffers passed to {@code onNext} again, and the subscriber
 * itself drops its only reference to the list when it completes.
 */
final class MultiByteBufferDataStream implements DataStream {

    private static final int COPY_BUF_SIZE = 16384;

    private final List<ByteBuffer> buffers;
    private final long contentLength;
    private final String contentType;
    private volatile ByteBuffer stitched;

    MultiByteBufferDataStream(List<ByteBuffer> buffers, long contentLength, String contentType) {
        if (buffers.size() < 2) {
            throw new IllegalArgumentException("MultiByteBufferDataStream requires >= 2 buffers; "
                    + "use DataStream.ofByteBuffers for the general factory.");
        }
        this.buffers = buffers;
        this.contentLength = contentLength;
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
    public boolean hasKnownLength() {
        return true;
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
    public boolean hasByteBuffer() {
        // The contiguous buffer is materialized lazily by asByteBuffer().
        return false;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        var local = stitched;
        if (local != null) {
            return local.duplicate();
        }
        // First call materializes a single contiguous buffer and caches it for subsequent
        // calls. We size the destination from the known total content length to avoid any
        // growth/resize.
        var total = ByteBuffer.allocate((int) contentLength);
        for (var buf : buffers) {
            total.put(buf.duplicate());
        }
        total.flip();
        stitched = total;
        return total.duplicate();
    }

    @Override
    public InputStream asInputStream() {
        // Build a SequenceInputStream over per-buffer ByteArrayInputStreams. We chain
        // wrappers rather than aggregating once because `asInputStream` is allowed to be
        // a streaming view over the underlying data.
        var iter = buffers.iterator();
        return new SequenceInputStream(new Enumeration<>() {
            @Override
            public boolean hasMoreElements() {
                return iter.hasNext();
            }

            @Override
            public InputStream nextElement() {
                var buf = iter.next().duplicate();
                if (buf.hasArray()) {
                    return new ByteArrayInputStream(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
                }
                return ByteBufferUtils.byteBufferInputStream(buf);
            }
        });
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        byte[] scratch = null;
        for (var buf : buffers) {
            if (buf.hasArray()) {
                out.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
            } else {
                if (scratch == null) {
                    scratch = new byte[COPY_BUF_SIZE];
                }
                var dup = buf.duplicate();
                while (dup.hasRemaining()) {
                    int n = Math.min(scratch.length, dup.remaining());
                    dup.get(scratch, 0, n);
                    out.write(scratch, 0, n);
                }
            }
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new Subscription(subscriber));
    }

    private final class Subscription implements Flow.Subscription {
        private final AtomicBoolean done = new AtomicBoolean();
        private final Flow.Subscriber<? super ByteBuffer> subscriber;

        Subscription(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0 || !done.compareAndSet(false, true)) {
                return;
            }
            for (var buf : buffers) {
                subscriber.onNext(buf.duplicate());
            }
            subscriber.onComplete();
        }

        @Override
        public void cancel() {
            done.set(true);
        }
    }
}
