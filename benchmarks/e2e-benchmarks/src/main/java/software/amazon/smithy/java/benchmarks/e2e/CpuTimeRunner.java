/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Locale;
import software.amazon.smithy.java.benchmarks.ProcessCpuTime;

final class CpuTimeRunner {

    static final long DEFAULT_MIN_ITERATIONS = 50_000;
    static final double DEFAULT_MIN_CPU_SECONDS = 5.0;
    static final int DEFAULT_CHECK_INTERVAL = 100;
    /** Linux process CPU time uses 10 ms ticks. A one-second measurement floor reduces rounding error. */
    static final double DEFAULT_MIN_MEASURE_CPU_SECONDS = 1.0;

    static final double UNDER_WARMED_JIT_SHARE = 0.05;

    private static final CompilationMXBean COMPILATION = ManagementFactory.getCompilationMXBean();
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final List<GarbageCollectorMXBean> COLLECTORS = ManagementFactory.getGarbageCollectorMXBeans();

    private final Settings settings;

    CpuTimeRunner(Settings settings) {
        this.settings = settings;
    }

    /** Calls beforeMeasurement after warmup and GC, immediately before measurement. */
    Measurement run(Invocation invocation, Runnable beforeMeasurement) throws Throwable {
        WarmupOutcome warmup = settings.warmup().automatic()
                ? warmupAutomatically(invocation)
                : warmupFixed(invocation, settings.warmup().iterations());
        System.gc();
        beforeMeasurement.run();

        long gcCountBefore = gcCount();
        long gcMillisBefore = gcMillis();
        long jitMillisBefore = jitMillis();
        long wallBefore = System.nanoTime();
        long threadCpuBefore = threadCpuNanos();
        long cpuBefore = ProcessCpuTime.now();

        long minIterations = settings.minIterations();
        long minCpuNanos = (long) (settings.minCpuSeconds() * 1_000_000_000L);
        long floorNanos = (long) (settings.minMeasureCpuSeconds() * 1_000_000_000L);
        boolean readCpu = minCpuNanos > 0 || floorNanos > 0;
        int checkInterval = settings.checkInterval();
        long iterations = 0;
        while (true) {
            invocation.run();
            iterations++;
            if (iterations % checkInterval == 0) {
                long elapsedCpu = readCpu ? ProcessCpuTime.now() - cpuBefore : 0;
                boolean reached = iterations >= minIterations || (minCpuNanos > 0 && elapsedCpu >= minCpuNanos);
                if (reached && elapsedCpu >= floorNanos) {
                    break;
                }
            }
        }

        long cpuAfter = ProcessCpuTime.now();
        long threadCpuAfter = threadCpuNanos();
        long wallAfter = System.nanoTime();
        return new Measurement(
                iterations,
                cpuAfter - cpuBefore,
                threadCpuAfter - threadCpuBefore,
                wallAfter - wallBefore,
                jitMillis() - jitMillisBefore,
                gcCount() - gcCountBefore,
                gcMillis() - gcMillisBefore,
                warmup);
    }

    private static WarmupOutcome warmupFixed(Invocation invocation, long iterations) throws Throwable {
        for (long i = 0; i < iterations; i++) {
            invocation.run();
        }
        return new WarmupOutcome(iterations, false, false, 0);
    }

    /** Use compiler activity to detect steady state. Throughput can appear stable before C2 installs optimized code. */
    private static WarmupOutcome warmupAutomatically(Invocation invocation) throws Throwable {
        long iterations = 0;
        int quietChunks = 0;
        long quietNanos = 0;
        double share = 0;
        long quietNanosRequired = (long) (Warmup.AUTO_QUIET_SECONDS * 1_000_000_000L);
        while (true) {
            long jitBefore = jitMillis();
            long wallBefore = System.nanoTime();
            for (int i = 0; i < Warmup.AUTO_CHUNK; i++) {
                invocation.run();
            }
            long wall = Math.max(1, System.nanoTime() - wallBefore);
            long jit = jitMillis() - jitBefore;
            iterations += Warmup.AUTO_CHUNK;
            share = jit * 1_000_000.0 / wall;
            if (share < Warmup.AUTO_QUIET_JIT_SHARE) {
                quietChunks++;
                quietNanos += wall;
            } else {
                quietChunks = 0;
                quietNanos = 0;
            }
            if (iterations >= Warmup.AUTO_FLOOR
                    && quietChunks >= Warmup.AUTO_QUIET_CHUNKS
                    && quietNanos >= quietNanosRequired) {
                return new WarmupOutcome(iterations, true, false, share);
            }
            if (iterations >= Warmup.AUTO_CAP) {
                return new WarmupOutcome(iterations, true, true, share);
            }
        }
    }

    static long jitMillis() {
        return COMPILATION != null && COMPILATION.isCompilationTimeMonitoringSupported()
                ? COMPILATION.getTotalCompilationTime()
                : 0;
    }

    private static long threadCpuNanos() {
        return THREADS.isCurrentThreadCpuTimeSupported() ? THREADS.getCurrentThreadCpuTime() : 0;
    }

    private static long gcCount() {
        long count = 0;
        for (var collector : COLLECTORS) {
            long c = collector.getCollectionCount();
            if (c > 0) {
                count += c;
            }
        }
        return count;
    }

    private static long gcMillis() {
        long millis = 0;
        for (var collector : COLLECTORS) {
            long t = collector.getCollectionTime();
            if (t > 0) {
                millis += t;
            }
        }
        return millis;
    }

    @FunctionalInterface
    interface Invocation {
        void run() throws Throwable;
    }

    /**
     * Stops at the iteration or CPU limit, subject to the measurement floor.
     * Zero minCpuSeconds disables the CPU limit. Zero minMeasureCpuSeconds disables the floor.
     */
    record Settings(
            long minIterations,
            double minCpuSeconds,
            int checkInterval,
            double minMeasureCpuSeconds,
            Warmup warmup) {

        Settings {
            if (minIterations <= 0) {
                throw new IllegalArgumentException("minIterations must be positive");
            }
            if (!Double.isFinite(minCpuSeconds) || minCpuSeconds < 0) {
                throw new IllegalArgumentException("minCpuSeconds must be finite and non-negative");
            }
            if (checkInterval <= 0) {
                throw new IllegalArgumentException("checkInterval must be positive");
            }
            if (!Double.isFinite(minMeasureCpuSeconds) || minMeasureCpuSeconds < 0) {
                throw new IllegalArgumentException("minMeasureCpuSeconds must be finite and non-negative");
            }
        }

        static Settings standard() {
            return standard(DEFAULT_MIN_MEASURE_CPU_SECONDS);
        }

        static Settings standard(double minMeasureCpuSeconds) {
            return new Settings(
                    DEFAULT_MIN_ITERATIONS,
                    DEFAULT_MIN_CPU_SECONDS,
                    DEFAULT_CHECK_INTERVAL,
                    minMeasureCpuSeconds,
                    Warmup.auto());
        }

        String stopCondition() {
            String base;
            if (minCpuSeconds > 0) {
                base = String.format(
                        Locale.ROOT,
                        "min %d iterations OR %s seconds CPU time (first met wins)",
                        minIterations,
                        formatSeconds(minCpuSeconds));
            } else {
                base = "fixed " + minIterations + " iterations for every benchmark";
            }
            if (minMeasureCpuSeconds > 0) {
                base += String.format(
                        Locale.ROOT,
                        " and at least %s second(s) of CPU time",
                        formatSeconds(minMeasureCpuSeconds));
            }
            if (minCpuSeconds <= 0) {
                base += ", no time-based stop";
            }
            return base;
        }

        private static String formatSeconds(double seconds) {
            return seconds == Math.rint(seconds) ? Long.toString((long) seconds) : Double.toString(seconds);
        }
    }

    record Warmup(boolean automatic, long iterations) {

        static final int AUTO_CHUNK = 2_000;
        static final long AUTO_FLOOR = 20_000;
        static final long AUTO_CAP = 2_000_000;
        static final int AUTO_QUIET_CHUNKS = 5;
        static final double AUTO_QUIET_JIT_SHARE = 0.02;
        static final double AUTO_QUIET_SECONDS = 1.0;

        static Warmup auto() {
            return new Warmup(true, 0);
        }

        static Warmup fixed(long iterations) {
            if (iterations < 0) {
                throw new IllegalArgumentException("warmup iterations must not be negative");
            }
            return new Warmup(false, iterations);
        }

        String policy() {
            return automatic ? "auto" : "fixed";
        }

        String detail() {
            if (automatic) {
                return String.format(
                        Locale.ROOT,
                        "chunks of %d iterations; stop once JIT compilation stays below %.0f%% of chunk wall time "
                                + "for %d consecutive chunks and %.0f second(s); floor %d, cap %d iterations",
                        AUTO_CHUNK,
                        AUTO_QUIET_JIT_SHARE * 100,
                        AUTO_QUIET_CHUNKS,
                        AUTO_QUIET_SECONDS,
                        AUTO_FLOOR,
                        AUTO_CAP);
            }
            return "fixed " + iterations + " iterations";
        }
    }

    record WarmupOutcome(long iterations, boolean automatic, boolean hitCap, double finalChunkJitShare) {}

    record Measurement(
            long iterations,
            long processCpuNanos,
            long threadCpuNanos,
            long wallNanos,
            long jitMillis,
            long gcCount,
            long gcMillis,
            WarmupOutcome warmup) {

        double opsPerCpuSecond() {
            return ProcessCpuTime.operationsPerSecond(iterations, processCpuNanos);
        }

        double opsPerWallSecond() {
            return iterations * 1_000_000_000.0 / wallNanos;
        }

        /** Measures only benchmark-thread CPU time. Excludes JIT and GC threads. */
        double opsPerThreadCpuSecond() {
            return threadCpuNanos > 0 ? iterations * 1_000_000_000.0 / threadCpuNanos : 0;
        }

        double cpuWallRatio() {
            return (double) processCpuNanos / wallNanos;
        }

        double jitShareOfWall() {
            return jitMillis * 1_000_000.0 / wallNanos;
        }

        boolean underWarmed() {
            return jitShareOfWall() > UNDER_WARMED_JIT_SHARE;
        }
    }
}
