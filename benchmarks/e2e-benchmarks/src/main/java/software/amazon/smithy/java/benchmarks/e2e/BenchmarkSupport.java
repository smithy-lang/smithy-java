/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

abstract class BenchmarkSupport {

    protected final BenchmarkConfig config;
    private final int payloadSize;
    private final long[] measuredDurationsNs;
    private final AtomicInteger measuredCount = new AtomicInteger();
    private final ResourceMonitor monitor = new ResourceMonitor();

    BenchmarkSupport(BenchmarkConfig config, int payloadSize) {
        this.config = config;
        this.payloadSize = payloadSize;
        this.measuredDurationsNs = new long[config.measurementBatches() * config.batchActions()];
        printInit();
    }

    abstract void run();

    protected final void runMeasured(Action action) {
        try (ExecutorService pool =
                config.operation().sequential ? null : Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("\n=== WARMUP PHASE ===");
            for (int i = 0; i < config.warmupBatches(); i++) {
                System.out.println("Warmup batch " + (i + 1) + "/" + config.warmupBatches());
                executeBatch(pool, action, false);
            }
            measuredCount.set(0);

            System.out.println("\n=== MEASUREMENT PHASE ===");
            if (config.collectMetrics()) {
                monitor.start(config.metricsIntervalMs());
            }

            long startNs = System.nanoTime();
            int lastSampleCount = 0;
            for (int i = 0; i < config.measurementBatches(); i++) {
                System.out.println("\nMeasurement batch " + (i + 1) + "/" + config.measurementBatches());
                int operationsBefore = measuredCount.get();
                long batchStart = System.nanoTime();
                executeBatch(pool, action, true);
                long batchDuration = System.nanoTime() - batchStart;
                printBatchResults(operationsBefore, batchDuration);

                if (config.collectMetrics()) {
                    int now = monitor.sampleCount();
                    monitor.statsSnapshot(lastSampleCount).printCompact("  Resource Usage: ");
                    lastSampleCount = now;
                }
            }
            long totalDuration = System.nanoTime() - startNs;

            if (config.collectMetrics()) {
                monitor.stop().print();
            }

            System.out.println("\n=== OVERALL RESULTS ===");
            printOverall(totalDuration);
        }
    }

    private void executeBatch(ExecutorService pool, Action action, boolean measure) {
        if (config.operation().sequential) {
            for (int i = 0; i < config.batchActions(); i++) {
                int preparedIndex = action.prepare(i);
                long start = System.nanoTime();
                action.run(preparedIndex);
                if (measure) {
                    recordDuration(System.nanoTime() - start);
                }
            }
            return;
        }

        int concurrency = Runtime.getRuntime().availableProcessors()
                * Integer.getInteger("e2e.concurrency.multiplier", 4);
        var permits = new Semaphore(concurrency);
        var done = new CountDownLatch(config.batchActions());
        var error = new AtomicReference<Throwable>();
        for (int i = 0; i < config.batchActions(); i++) {
            permits.acquireUninterruptibly();
            final int index = action.prepare(i);
            pool.execute(() -> {
                try {
                    long start = System.nanoTime();
                    action.run(index);
                    if (measure) {
                        recordDuration(System.nanoTime() - start);
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    permits.release();
                    done.countDown();
                }
            });
        }
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for benchmark batch", e);
        }
        var failure = error.get();
        if (failure != null) {
            throw new RuntimeException("Benchmark task failed", failure);
        }
    }

    private void recordDuration(long durationNs) {
        measuredDurationsNs[measuredCount.getAndIncrement()] = durationNs;
    }

    private void printInit() {
        System.out.println("Initialized smithy-java e2e benchmark:");
        System.out.println("  Operation: " + config.operation().id);
        System.out.println("  Region:    " + config.region());
        if (config.operation().isS3()) {
            System.out.println("  Bucket:    " + config.bucketName());
            System.out.println("  Object:    " + config.objectSize() + " bytes");
        } else {
            System.out.println("  Table:     " + config.tableName());
        }
        System.out.println("  Sequential:        " + config.operation().sequential);
        System.out.println("  Actions per batch: " + config.batchActions());
    }

    private void printBatchResults(int startIndex, long batchDurationNs) {
        int endIndex = measuredCount.get();
        if (endIndex <= startIndex) {
            System.out.println("  No operations in this batch");
            return;
        }
        var batch = Arrays.copyOfRange(measuredDurationsNs, startIndex, endIndex);
        Arrays.sort(batch);
        int count = batch.length;
        double batchSec = batchDurationNs / 1e9;
        long totalBytes = (long) count * payloadSize;
        double gbps = (totalBytes * 8.0 / batchSec) / 1e9;
        long max = batch[count - 1];
        long p50 = batch[count / 2];
        long p90 = batch[(int) (count * 0.9)];
        long p99 = batch[(int) (count * 0.99)];
        double avgNs = average(batch, count);
        System.out.printf("  Operations: %d, Duration: %.2fs, Throughput: %.2f Gbps%n",
                count,
                batchSec,
                gbps);
        System.out.printf("  Latency (ms) - Avg: %.2f, P50: %.2f, P90: %.2f, P99: %.2f, Max: %.2f%n",
                avgNs / 1e6,
                p50 / 1e6,
                p90 / 1e6,
                p99 / 1e6,
                max / 1e6);
    }

    private void printOverall(long totalDurationNs) {
        int count = measuredCount.get();
        if (count == 0) {
            System.out.println("No measurements collected");
            return;
        }
        Arrays.sort(measuredDurationsNs, 0, count);
        double totalSec = totalDurationNs / 1e9;
        long totalBytes = (long) count * payloadSize;
        double gbps = (totalBytes * 8.0 / totalSec) / 1e9;
        long min = measuredDurationsNs[0];
        long max = measuredDurationsNs[count - 1];
        long p50 = measuredDurationsNs[count / 2];
        long p90 = measuredDurationsNs[(int) (count * 0.9)];
        long p99 = measuredDurationsNs[(int) (count * 0.99)];
        double avgNs = average(measuredDurationsNs, count);
        System.out.println("Total operations: " + count);
        System.out.printf("Total data transferred: %.2f MiB%n", totalBytes / 1024.0 / 1024.0);
        System.out.printf("Total duration: %.2f seconds%n", totalSec);
        System.out.printf("Throughput: %.2f Gbps%n", gbps);
        System.out.println("\nLatency (milliseconds):");
        System.out.printf("  Average: %.2f%n", avgNs / 1e6);
        System.out.printf("  Minimum: %.2f%n", min / 1e6);
        System.out.printf("  P50:     %.2f%n", p50 / 1e6);
        System.out.printf("  P90:     %.2f%n", p90 / 1e6);
        System.out.printf("  P99:     %.2f%n", p99 / 1e6);
        System.out.printf("  Maximum: %.2f%n", max / 1e6);
    }

    private static double average(long[] values, int count) {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += values[i];
        }
        return count == 0 ? 0 : (double) sum / count;
    }

    @FunctionalInterface
    protected interface Action {
        default int prepare(int index) {
            return index;
        }

        void run(int index);
    }
}
