/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

/**
 * A pooled stream that reads another pooled stream's storage and forwards the lease to it.
 *
 * <p>Override {@link #escaped()} when the wrapper keeps a private copy of its own.
 */
public interface ForwardingPooledDataStream extends PooledDataStream {
    /**
     * @return the pooled stream whose storage this stream reads
     */
    PooledDataStream lease();

    @Override
    default void release() {
        lease().release();
    }

    @Override
    default boolean isReleased() {
        return lease().isReleased();
    }

    @Override
    default void abandon() {
        lease().abandon();
    }

    @Override
    default boolean escaped() {
        return lease().escaped();
    }
}
