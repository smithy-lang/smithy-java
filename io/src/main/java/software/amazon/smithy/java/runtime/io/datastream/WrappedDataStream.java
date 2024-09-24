/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.datastream;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

final class WrappedDataStream implements DataStream {

    private final DataStream delegate;
    private final String contentType;
    private final long contentLength;

    WrappedDataStream(DataStream delegate, long contentLength, String contentType) {
        this.delegate = delegate;
        this.contentLength = contentLength;
        this.contentType = contentType;
    }

    @Override
    public CompletableFuture<ByteBuffer> asByteBuffer() {
        return delegate.asByteBuffer();
    }

    @Override
    public CompletableFuture<InputStream> asInputStream() {
        return delegate.asInputStream();
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
    public ByteBuffer waitForByteBuffer() {
        return delegate.waitForByteBuffer();
    }

    @Override
    public boolean hasByteBuffer() {
        return delegate.hasByteBuffer();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        delegate.subscribe(subscriber);
    }
}
