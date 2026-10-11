/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

/**
 * A replayable in-memory stream with pooled storage.
 *
 * <p>The client pipeline owns request leases and releases them after the call completes, including retries.
 * Call {@link #copy()} before release to retain the content.
 * Reads after release throw {@link IllegalStateException}.
 *
 * <p>Read methods use storage as follows:
 * <ul>
 *     <li>{@code writeTo}, {@link #forEachBuffer}, and {@link #copy()} read pooled storage directly.</li>
 *     <li>{@link #asByteBuffer()}, {@link #asInputStream()}, {@link #asChannel()}, and {@code subscribe}
 *         use a private copy created on the first such read and reused for later reads.
 *         These views remain valid after release.</li>
 * </ul>
 *
 * <p>Pooled streams are not thread-safe.
 * Complete all reads before release. If a reader can continue after the transport returns, call {@link #abandon()}.
 *
 * <p>Wrappers that read pooled storage must implement this interface and forward its lifecycle methods.
 */
public interface PooledDataStream extends DataStream {
    /**
     * Ends the lease and returns its storage to the pool unless abandoned.
     * Complete all reads before calling this method. Repeated calls are safe.
     */
    void release();

    /**
     * @return true if the stream was released and can no longer be read
     */
    boolean isReleased();

    /**
     * Prevents recycling when an uncontrolled reader can still access pooled storage.
     * Call this before returning from a transport while an upload worker can still read.
     * Calls after release have no effect.
     */
    void abandon();

    /**
     * @return true if a read created a private copy or {@link #abandon()} was called before release
     */
    boolean escaped();

    /**
     * Copies the content into an unpooled stream.
     *
     * @return a replayable copy with the same content type
     * @throws IllegalStateException if this stream was released
     */
    DataStream copy();

    /**
     * Does nothing so the body remains available for retries.
     * Use {@link #release()} to end the lease.
     */
    @Override
    default void close() {}
}
