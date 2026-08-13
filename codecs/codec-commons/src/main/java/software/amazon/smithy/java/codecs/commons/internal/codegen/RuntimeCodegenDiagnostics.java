/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import java.util.concurrent.atomic.LongAdder;

public final class RuntimeCodegenDiagnostics {
    private final LongAdder requests = new LongAdder();
    private final LongAdder hits = new LongAdder();
    private final LongAdder successes = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder fallbacks = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final LongAdder emittedClasses = new LongAdder();
    private final LongAdder emittedBytes = new LongAdder();
    private final LongAdder generationNanos = new LongAdder();

    void request() {
        requests.increment();
    }

    void hit() {
        hits.increment();
    }

    void success(int bytes, long nanos) {
        successes.increment();
        emittedClasses.increment();
        emittedBytes.add(bytes);
        generationNanos.add(nanos);
    }

    void failure(long nanos) {
        failures.increment();
        generationNanos.add(nanos);
    }

    public void fallback() {
        fallbacks.increment();
    }

    void eviction() {
        evictions.increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                requests.sum(),
                hits.sum(),
                successes.sum(),
                failures.sum(),
                fallbacks.sum(),
                evictions.sum(),
                emittedClasses.sum(),
                emittedBytes.sum(),
                generationNanos.sum());
    }

    public record Snapshot(
            long requests,
            long hits,
            long successes,
            long failures,
            long fallbacks,
            long evictions,
            long emittedClasses,
            long emittedBytes,
            long generationNanos) {}
}
