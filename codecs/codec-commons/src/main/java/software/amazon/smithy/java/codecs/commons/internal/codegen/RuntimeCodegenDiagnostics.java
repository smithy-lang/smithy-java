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
    private volatile String lastFailureType;
    private volatile String lastFailureMessage;
    private volatile String lastFailureLocation;

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

    void failure(Throwable error, long nanos) {
        failures.increment();
        generationNanos.add(nanos);
        lastFailureType = error.getClass().getName();
        lastFailureMessage = error.getMessage();
        StackTraceElement[] trace = error.getStackTrace();
        lastFailureLocation = trace.length == 0 ? null : trace[0].toString();
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
                generationNanos.sum(),
                lastFailureType,
                lastFailureMessage,
                lastFailureLocation);
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
            long generationNanos,
            String lastFailureType,
            String lastFailureMessage,
            String lastFailureLocation) {}
}
