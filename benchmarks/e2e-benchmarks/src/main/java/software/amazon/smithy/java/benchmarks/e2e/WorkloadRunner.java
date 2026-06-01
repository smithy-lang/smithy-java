/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.AttributeValue;

/**
 * smithy-java implementation of the e2e benchmark workload runner. Reads the
 * shared workload JSON spec and produces results comparable to other SDK runners.
 */
public final class WorkloadRunner {

    private final WorkloadConfig workload;
    private final ActionExecutor executor;
    private final int payloadSize;
    private final long[] measuredDurationsNs;
    private final AtomicInteger measuredCount = new AtomicInteger();
    private final ResourceMonitor monitor = new ResourceMonitor();
    // Hoist actionConfig string/int reads out of the per-request hot path. These were resolved
    // anew on every executeAction call before, costing one Optional + ObjectNode lookup each.
    private final String service;
    private final String action;
    private final String bucketName;
    private final String tableName;
    private final String keyPrefix;
    private final int objectSize;
    private final int dataLength;

    private WorkloadRunner(WorkloadConfig workload) {
        this.workload = workload;
        this.payloadSize = maxPayloadSize(workload);
        this.measuredDurationsNs = new long[workload.measurementBatches * workload.batchActions];
        byte[] payload = new byte[payloadSize];
        new Random(0xC0FFEEL).nextBytes(payload);

        var region = workload.stringConfig("region");
        this.service = workload.service;
        this.action = workload.action;
        this.bucketName = "s3".equals(service) ? workload.stringConfig("bucketName") : null;
        this.tableName = "dynamodb".equals(service) ? workload.stringConfig("tableName") : null;
        this.keyPrefix = workload.stringConfig("keyPrefix");
        this.objectSize = "s3".equals(service) ? workload.intConfig("objectSize") : 0;
        this.dataLength = workload.actionConfig.getMember("dataLength").isPresent()
                ? workload.intConfig("dataLength")
                : 0;

        var ddb = "dynamodb".equals(service) ? Clients.dynamodb(region) : null;
        var s3 = "s3".equals(service) ? Clients.s3(region) : null;
        if (ddb == null && s3 == null) {
            throw new IllegalArgumentException("Unknown service: " + service);
        }
        this.executor = new ActionExecutor(ddb, s3, payload);

        System.out.println("Initialized smithy-java WorkloadRunner:");
        System.out.println("  Workload: " + workload.name);
        System.out.println("  Service:  " + service);
        System.out.println("  Action:   " + action);
        System.out.println("  Region:   " + region);
        System.out.println("  Sequential:        " + workload.sequential);
        System.out.println("  Actions per batch: " + workload.batchActions);
    }

    private static int maxPayloadSize(WorkloadConfig w) {
        if ("s3".equals(w.service)) {
            return w.intConfig("objectSize");
        }
        // DDB putitem has dataLength; getitem doesn't. Either way 1KiB is
        // a safe default that won't be hit on getitem.
        if (w.actionConfig.getMember("dataLength").isPresent()) {
            return w.intConfig("dataLength");
        }
        return 1024;
    }

    private void run() {
        try (ExecutorService pool = workload.sequential ? null : Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("\n=== WARMUP PHASE ===");
            System.out.println("Executing " + workload.warmupBatches + " warmup batches to initialize SDK clients...");
            for (int i = 0; i < workload.warmupBatches; i++) {
                System.out.println("Warmup batch " + (i + 1) + "/" + workload.warmupBatches);
                executeBatch(pool, false);
            }
            measuredCount.set(0);

            System.out.println("\n=== MEASUREMENT PHASE ===");
            System.out.println("Executing " + workload.measurementBatches + " measurement batches...");

            if (workload.collectMetrics) {
                monitor.start(workload.metricsIntervalMs);
            }

            long startNs = System.nanoTime();
            int lastSampleCount = 0;
            for (int i = 0; i < workload.measurementBatches; i++) {
                System.out.println("\nMeasurement batch " + (i + 1) + "/" + workload.measurementBatches);
                int operationsBefore = measuredCount.get();
                long batchStart = System.nanoTime();
                executeBatch(pool, true);
                long batchDuration = System.nanoTime() - batchStart;
                printBatchResults(operationsBefore, batchDuration);

                if (workload.collectMetrics) {
                    int now = monitor.sampleCount();
                    monitor.statsSnapshot(lastSampleCount).printCompact("  Resource Usage: ");
                    lastSampleCount = now;
                }
            }
            long totalDuration = System.nanoTime() - startNs;

            if (workload.collectMetrics) {
                monitor.stop().print();
            }

            System.out.println("\n=== OVERALL RESULTS ===");
            printOverall(totalDuration);
        }
    }

    private void executeBatch(ExecutorService pool, boolean measure) {
        if (workload.sequential) {
            for (int i = 0; i < workload.batchActions; i++) {
                long s = System.nanoTime();
                executeAction(i);
                long d = System.nanoTime() - s;
                if (measure) {
                    recordDuration(d);
                }
            }
        } else {
            // smithy-java's blocking client is driven from a virtual-thread executor: each
            // action task gets its own virtual thread that blocks on the HTTP call, no platform
            // thread is held while the call is in flight. The submitting thread acquires a
            // permit before submitting so only `concurrency` tasks are ever in flight at once.
            // Multiplier configurable via -De2e.concurrency.multiplier so we can sweep
            // without rebuilding. Default is 4× cores.
            //
            // Each task writes its duration into the preallocated long[] directly. The latch lets
            // the submitting thread wait for completion without retaining one Future per action.
            int concurrency = Runtime.getRuntime().availableProcessors()
                    * Integer.getInteger("e2e.concurrency.multiplier", 4);
            var permits = new Semaphore(concurrency);
            var done = new CountDownLatch(workload.batchActions);
            var error = new AtomicReference<Throwable>();
            for (int i = 0; i < workload.batchActions; i++) {
                permits.acquireUninterruptibly();
                final int index = i;
                pool.execute(() -> {
                    try {
                        long s = System.nanoTime();
                        executeAction(index);
                        if (measure) {
                            recordDuration(System.nanoTime() - s);
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
    }

    private void recordDuration(long durationNs) {
        measuredDurationsNs[measuredCount.getAndIncrement()] = durationNs;
    }

    private void executeAction(int index) {
        try {
            if ("s3".equals(service)) {
                var key = generateKey(index);
                if ("upload".equals(action)) {
                    executor.putObject(bucketName, key, objectSize);
                } else if ("download".equals(action)) {
                    executor.getObject(bucketName, key);
                } else {
                    throw new IllegalArgumentException("Unknown S3 action: " + action);
                }
            } else if ("dynamodb".equals(service)) {
                if ("putitem".equals(action)) {
                    executor.putItem(tableName, buildItem(index));
                } else if ("getitem".equals(action)) {
                    var pk = AttributeValue.builder().s(generateKey(index)).build();
                    executor.getItem(tableName, Map.of("pk", pk));
                } else {
                    throw new IllegalArgumentException("Unknown DynamoDB action: " + action);
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Action failed: service=" + service + ", action=" + action);
            System.err.println("Error: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.err
                        .println("Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            throw e;
        }
    }

    private Map<String, AttributeValue> buildItem(int index) {
        var pk = AttributeValue.builder().s(generateKey(index)).build();
        var data = AttributeValue.builder().s(randomString(dataLength)).build();
        return Map.of("pk", pk, "data", data);
    }

    private String generateKey(int index) {
        return keyPrefix + (index + 1);
    }

    private static String randomString(int length) {
        // Reference runner uses [A-Za-z0-9]; matched here for byte-for-byte compatibility on the
        // wire. ThreadLocalRandom avoids the per-call Random allocation that a fresh `new Random()`
        // would require.
        var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        var random = ThreadLocalRandom.current();
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
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

    public static void main(String[] args) throws Exception {
        // Set JDK HttpClient system properties BEFORE anything (including IMDS) constructs an
        // HttpClient. The JDK HttpClient reads these in its static initializers and caches the
        // values; later changes have no effect on already-constructed clients. The benchmark
        // runner's main() is the only safe place to set them — earlier than the IMDS credential
        // provider's bootstrap and earlier than JavaHttpClientTransport's class load.
        var restricted = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
        if (restricted == null || restricted.isEmpty()) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        } else if (!restricted.contains("host")) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", restricted + ",host");
        }
        // Buffer + frame sizes — overridable via -Djdk.httpclient.bufsize / -Djdk.httpclient.maxframesize
        // on the command line. We default to 64 KiB but only set the property if the user didn't.
        if (System.getProperty("jdk.httpclient.bufsize") == null) {
            System.setProperty("jdk.httpclient.bufsize", "65536");
        }
        if (System.getProperty("jdk.httpclient.maxframesize") == null) {
            System.setProperty("jdk.httpclient.maxframesize", "65536");
        }
        // Force smithy-java's native JSON provider over Jackson. Jackson is
        // bundled because some smithy-java modules pull it in transitively;
        // its priority (10) outranks the smithy provider (5), so we'd
        // silently use Jackson without this. Set before any JsonCodec
        // initializes — the provider is selected in a static initializer.
        if (System.getProperty("smithy-java.json-provider") == null) {
            System.setProperty("smithy-java.json-provider", "smithy");
        }
        // Silence per-call INFO logging from the rules engine endpoint
        // resolver. It logs once per client.build() call, but more
        // importantly it formats arguments on every emit even if a downstream
        // handler filters them — extra work on the hot path. Bump JUL's
        // root level so the System.Logger backend short-circuits. Skip the
        // reset when -Dsmithy.bench.debug=true so wire logging stays visible.
        if (!"true".equals(System.getProperty("smithy.bench.debug"))) {
            LogManager.getLogManager().reset();
            var rootLogger = Logger.getLogger("");
            rootLogger.setLevel(java.util.logging.Level.WARNING);
        }
        //   java -jar runner.jar [--client sync|async]
        //                        [--bucket <name>] [--table <name>] [--region <region>]
        //                        <workload-file>
        // smithy-java only generates synchronous clients; --client is accepted for parity with
        // other runners but only "sync" is supported. The --bucket / --table / --region flags
        // override the corresponding fields in the workload's actionConfig so the same workload
        // JSON works across environments without editing.
        String workloadPath = null;
        Map<String, String> overrides = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--client" -> {
                    String mode = requireValue(args, ++i, "--client");
                    if ("async".equals(mode)) {
                        System.err.println("WARNING: smithy-java does not generate async clients. "
                                + "Running with the synchronous client; throughput tests will use a thread pool.");
                    } else if (!"sync".equals(mode)) {
                        fail("Error: Invalid client mode '" + mode + "'. Valid values are: sync, async");
                    }
                }
                case "--bucket" -> overrides.put("bucketName", requireValue(args, ++i, "--bucket"));
                case "--table" -> overrides.put("tableName", requireValue(args, ++i, "--table"));
                case "--region" -> overrides.put("region", requireValue(args, ++i, "--region"));
                default -> {
                    if (args[i].startsWith("--")) {
                        fail("Error: Unknown flag '" + args[i] + "'");
                    }
                    if (workloadPath != null) {
                        fail("Error: Unexpected positional argument '" + args[i] + "'");
                    }
                    workloadPath = args[i];
                }
            }
        }
        if (workloadPath == null) {
            fail("Usage: java -jar smithy-java-e2e-benchmark-runner.jar [--client sync|async]"
                    + " [--bucket <name>] [--table <name>] [--region <region>] <workload-file>");
        }
        var workload = WorkloadConfig.load(workloadPath);
        if (!overrides.isEmpty()) {
            workload.overrideActionConfig(overrides);
        }
        printActiveJsonProvider();
        new WorkloadRunner(workload).run();
    }

    private static String requireValue(String[] args, int i, String flag) {
        if (i >= args.length) {
            fail("Error: " + flag + " requires a value");
        }
        return args[i];
    }

    private static void printActiveJsonProvider() {
        // Reflectively read JsonSettings.PROVIDER so we can confirm which implementation actually got picked
        try {
            var clazz = Class.forName("software.amazon.smithy.java.json.JsonSettings");
            var field = clazz.getDeclaredField("PROVIDER");
            field.setAccessible(true);
            Object provider = field.get(null);
            var nameMethod = provider.getClass().getMethod("getName");
            System.out.println("Active JSON provider: " + nameMethod.invoke(provider));
        } catch (Exception e) {
            System.err.println("Could not determine active JSON provider: " + e);
        }
    }

    private static void fail(String msg) {
        System.err.println(msg);
        System.exit(1);
    }
}
