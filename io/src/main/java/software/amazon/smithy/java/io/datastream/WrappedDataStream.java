/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

final class WrappedDataStream implements DataStream {

    private final DataStream delegate;
    private final String contentType;
    private final long contentLength;
    private final boolean isReplayable;

    WrappedDataStream(DataStream delegate, long contentLength, String contentType, boolean isReplayable) {
        this.delegate = delegate;
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.isReplayable = isReplayable;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return delegate.asByteBuffer();
    }

    @Override
    public InputStream asInputStream() {
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
    public boolean isReplayable() {
        return isReplayable;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        delegate.subscribe(subscriber);
    }
}
