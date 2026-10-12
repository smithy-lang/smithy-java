/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

final class WrappedPooledDataStream extends WrappedDataStream implements ForwardingPooledDataStream {

    private final PooledDataStream pooled;

    WrappedPooledDataStream(PooledDataStream delegate, long contentLength, String contentType, boolean isReplayable) {
        super(delegate, contentLength, contentType, isReplayable);
        this.pooled = delegate;
    }

    @Override
    public PooledDataStream lease() {
        return pooled;
    }

    @Override
    public DataStream copy() {
        return DataStream.withMetadata(pooled.copy(), contentType(), contentLength(), isReplayable());
    }

    @Override
    public void close() {}

    @Override
    public String toString() {
        return "WrappedPooledDataStream{contentType=" + contentType() + ", contentLength=" + contentLength()
                + ", delegate=" + pooled + '}';
    }
}
