/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.datastream;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.runtime.io.ByteBufferUtils;
import software.amazon.smithy.java.runtime.io.Slice;

final class SliceDataStream implements DataStream {

    private final Slice slice;
    private final String contentType;
    private Flow.Publisher<ByteBuffer> publisher;

    SliceDataStream(Slice slice, String contentType) {
        this.slice = slice;
        this.contentType = contentType;
    }

    @Override
    public long contentLength() {
        return slice.length();
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
    public Slice waitForSlice() {
        return slice;
    }

    @Override
    public ByteBuffer waitForByteBuffer() {
        return slice.toByteBuffer();
    }

    @Override
    public boolean hasBytes() {
        return true;
    }

    @Override
    public boolean isReplayable() {
        return true;
    }

    @Override
    public CompletableFuture<Slice> asSlice() {
        return CompletableFuture.completedFuture(slice);
    }

    @Override
    public CompletableFuture<ByteBuffer> asByteBuffer() {
        return CompletableFuture.completedFuture(slice.toByteBuffer());
    }

    @Override
    public CompletableFuture<InputStream> asInputStream() {
        return CompletableFuture.completedFuture(ByteBufferUtils.byteBufferInputStream(slice.toByteBuffer()));
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        var p = publisher;
        if (p == null) {
            publisher = p = HttpRequest.BodyPublishers.ofByteArray(slice.bytes(), slice.offset(), slice.length());
        }
        p.subscribe(subscriber);
    }
}
