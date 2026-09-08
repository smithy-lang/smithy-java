/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Process-wide counters describing runtime codec generation outcomes, keyed by backend id.
 *
 * <p>Generation is attempted once per {@code (shape, settings)} pair, so these counters are only
 * touched on a cache miss and never from a serialization or deserialization hot path.
 *
 * <p>They exist because generation failure is, by contract, invisible: an unsupported shape falls
 * back to the dispatch serde and the caller still gets a correct answer. That makes a test suite
 * running with generation enabled indistinguishable from one running entirely on the fallback.
 * Assert against a {@link #snapshot(String)} to prove generation actually engaged.
 */
@SmithyInternalApi
public final class RuntimeCodegenStats {
    private static final ConcurrentHashMap<String, Counters> BACKENDS = new ConcurrentHashMap<>();

    private RuntimeCodegenStats() {}

    /** A generated codec was emitted, loaded, and published. */
    public static void recordGenerated(String backend) {
        counters(backend).generated.increment();
    }

    /** The schema graph is outside what the backend supports; the caller falls back by design. */
    public static void recordUnsupported(String backend) {
        counters(backend).unsupported.increment();
    }

    /** Generation broke. Always a bug in the backend, the plan, or the classfile emitter. */
    public static void recordFailed(String backend) {
        counters(backend).failed.increment();
    }

    public static Snapshot snapshot(String backend) {
        Counters counters = BACKENDS.get(backend);
        return counters == null ? Snapshot.EMPTY : counters.snapshot();
    }

    public static Map<String, Snapshot> snapshots() {
        return BACKENDS.entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().snapshot()));
    }

    public static void reset() {
        BACKENDS.clear();
    }

    private static Counters counters(String backend) {
        return BACKENDS.computeIfAbsent(backend, ignored -> new Counters());
    }

    /**
     * An immutable read of one backend's counters.
     *
     * @param generated codecs successfully emitted and published
     * @param unsupported shapes rejected as unsupported, which fall back by design
     * @param failed generation attempts that broke, which fall back but indicate a bug
     */
    public record Snapshot(long generated, long unsupported, long failed) {
        private static final Snapshot EMPTY = new Snapshot(0, 0, 0);

        public long attempts() {
            return generated + unsupported + failed;
        }
    }

    private static final class Counters {
        private final LongAdder generated = new LongAdder();
        private final LongAdder unsupported = new LongAdder();
        private final LongAdder failed = new LongAdder();

        private Snapshot snapshot() {
            return new Snapshot(generated.sum(), unsupported.sum(), failed.sum());
        }
    }
}
