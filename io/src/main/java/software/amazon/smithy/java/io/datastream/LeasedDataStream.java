/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

final class LeasedDataStream implements PooledDataStream {
    private final byte[] array;
    private final int offset;
    private final int length;
    private final String contentType;
    private final ByteArrayRecycler recycler;
    private byte[] aliasCopy;
    private boolean abandoned;
    private boolean released;

    LeasedDataStream(byte[] array, int offset, int length, String contentType, ByteArrayRecycler recycler) {
        this.array = Objects.requireNonNull(array, "array");
        Objects.checkFromIndexSize(offset, length, array.length);
        this.offset = offset;
        this.length = length;
        this.contentType = contentType;
        this.recycler = Objects.requireNonNull(recycler, "recycler");
    }

    @Override
    public long contentLength() {
        return length;
    }

    @Override
    public boolean hasKnownLength() {
        return true;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public boolean isReplayable() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return !released;
    }

    @Override
    public boolean hasByteBuffer() {
        return true;
    }

    @Override
    public boolean isReleased() {
        return released;
    }

    @Override
    public boolean escaped() {
        return aliasCopy != null || abandoned;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(aliasCopy());
    }

    @Override
    public InputStream asInputStream() {
        return new ByteArrayInputStream(aliasCopy());
    }

    @Override
    public ReadableByteChannel asChannel() {
        return Channels.newChannel(asInputStream());
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        DataStream.ofByteBuffer(asByteBuffer(), contentType).subscribe(subscriber);
    }

    private byte[] aliasCopy() {
        checkNotReleased();
        byte[] copy = aliasCopy;
        if (copy == null) {
            copy = aliasCopy = Arrays.copyOfRange(array, offset, offset + length);
        }
        return copy;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        checkNotReleased();
        out.write(array, offset, length);
    }

    @Override
    public void writeTo(WritableByteChannel channel) throws IOException {
        checkNotReleased();
        ByteBuffer view = ByteBuffer.wrap(array, offset, length);
        while (view.hasRemaining()) {
            channel.write(view);
        }
    }

    @Override
    public void forEachBuffer(Consumer<ByteBuffer> action) {
        checkNotReleased();
        action.accept(ByteBuffer.wrap(array, offset, length).slice());
    }

    @Override
    public DataStream copy() {
        checkNotReleased();
        return DataStream.ofBytes(Arrays.copyOfRange(array, offset, offset + length), contentType);
    }

    @Override
    public void discard() {}

    @Override
    public void abandon() {
        if (!released) {
            abandoned = true;
        }
    }

    @Override
    public void release() {
        if (released) {
            return;
        }
        released = true;
        if (!abandoned) {
            recycler.recycle(array);
        }
    }

    private void checkNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "This pooled request body was released when its call completed and cannot be read again. "
                            + "Code that keeps a request past its call must copy the body while the call is in progress.");
        }
    }

    @Override
    public String toString() {
        return "LeasedDataStream{length=" + length
                + ", contentType=" + contentType
                + ", aliased=" + (aliasCopy != null)
                + ", abandoned=" + abandoned
                + ", released=" + released
                + '}';
    }
}
