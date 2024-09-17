/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.common.datastream;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
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
    public CompletionStage<ByteBuffer> asByteBuffer() {
        return delegate.asByteBuffer();
    }

    @Override
    public CompletionStage<byte[]> asBytes() {
        return delegate.asBytes();
    }

    @Override
    public CompletionStage<InputStream> asInputStream() {
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
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        delegate.subscribe(subscriber);
    }
}
