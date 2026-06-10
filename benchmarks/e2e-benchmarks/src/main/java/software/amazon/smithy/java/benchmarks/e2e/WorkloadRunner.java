/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.AttributeValue;

/**
 * smithy-java implementation of the e2e benchmark workload runner. Reads the
 * workload JSON spec and produces results comparable to the reference Java SDK
 * v2 runner.
 *
 * <p>Limitations:
 *
 * <ul>
 *     <li>smithy-java's generated clients are synchronous; there is no
 *         {@code xxxAsync} API. The {@code --client async} flag from the
 *         reference runner is not accepted. Throughput-style workloads still
 *         drive concurrency through a thread pool, which is the SDK's
 *         idiomatic concurrency pattern.</li>
 * </ul>
 */
public final class WorkloadRunner {

    private final WorkloadConfig workload;
    private final ActionExecutor executor;
    private final byte[] payload;
    private final int payloadSize;
    private final List<Long> measuredDurationsNs = Collections.synchronizedList(new ArrayList<>());
    private final ResourceMonitor monitor = new ResourceMonitor();

    private WorkloadRunner(WorkloadConfig workload) {
        this.workload = workload;
        this.payloadSize = maxPayloadSize(workload);
        this.payload = new byte[payloadSize];
        new Random(0xC0FFEEL).nextBytes(payload);

        var region = workload.stringConfig("region");
        var ddb = "dynamodb".equals(workload.service) ? Clients.dynamodb(region) : null;
        var s3 = "s3".equals(workload.service) ? Clients.s3(region) : null;
        if (ddb == null && s3 == null) {
            throw new IllegalArgumentException("Unknown service: " + workload.service);
        }
        // Build the unused client too — the executor stores nullable refs and
        // the runner only invokes the path matching workload.service.
        this.executor = new ActionExecutor(
                ddb == null ? Clients.dynamodb(region) : ddb,
                s3 == null ? Clients.s3(region) : s3,
                payload);

        System.out.println("Initialized smithy-java WorkloadRunner:");
        System.out.println("  Workload: " + workload.name);
        System.out.println("  Service:  " + workload.service);
        System.out.println("  Action:   " + workload.action);
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
        System.out.println("\n=== WARMUP PHASE ===");
        System.out.println("Executing " + workload.warmupBatches + " warmup batches to initialize SDK clients...");
        for (int i = 0; i < workload.warmupBatches; i++) {
            System.out.println("Warmup batch " + (i + 1) + "/" + workload.warmupBatches);
            executeBatch(false);
        }
        measuredDurationsNs.clear();

        System.out.println("\n=== MEASUREMENT PHASE ===");
        System.out.println("Executing " + workload.measurementBatches + " measurement batches...");

        if (workload.collectMetrics) {
            monitor.start(workload.metricsIntervalMs);
        }

        long startNs = System.nanoTime();
        int lastSampleCount = 0;
        for (int i = 0; i < workload.measurementBatches; i++) {
            System.out.println("\nMeasurement batch " + (i + 1) + "/" + workload.measurementBatches);
            int operationsBefore = measuredDurationsNs.size();
            long batchStart = System.nanoTime();
            executeBatch(true);
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

    private void executeBatch(boolean measure) {
        if (workload.sequential) {
            for (int i = 0; i < workload.batchActions; i++) {
                long s = System.nanoTime();
                executeAction(i);
                long d = System.nanoTime() - s;
                if (measure) {
                    measuredDurationsNs.add(d);
                }
            }
        } else {
            // The reference runner uses 2x cores; smithy-java's blocking
            // client is idiomatically driven from a thread pool, so match
            // it for fair comparison.
            int threads = Runtime.getRuntime().availableProcessors() * 2;
            ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
                var t = new Thread(r, "e2e-worker");
                t.setDaemon(true);
                return t;
            });
            List<Future<Long>> futures = new ArrayList<>(workload.batchActions);
            for (int i = 0; i < workload.batchActions; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    long s = System.nanoTime();
                    executeAction(index);
                    return System.nanoTime() - s;
                }));
            }
            for (var f : futures) {
                try {
                    long d = f.get();
                    if (measure) {
                        measuredDurationsNs.add(d);
                    }
                } catch (Exception e) {
                    System.err.println("Task failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            pool.shutdown();
            try {
                pool.awaitTermination(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void executeAction(int index) {
        var action = workload.action;
        try {
            if ("s3".equals(workload.service)) {
                var bucket = workload.stringConfig("bucketName");
                var key = generateKey(index);
                if ("upload".equals(action)) {
                    executor.putObject(bucket, key, workload.intConfig("objectSize"));
                } else if ("download".equals(action)) {
                    executor.getObject(bucket, key);
                } else {
                    throw new IllegalArgumentException("Unknown S3 action: " + action);
                }
            } else if ("dynamodb".equals(workload.service)) {
                var tableName = workload.stringConfig("tableName");
                if ("putitem".equals(action)) {
                    executor.putItem(tableName, buildItem(index));
                } else if ("getitem".equals(action)) {
                    Map<String, AttributeValue> key = new HashMap<>();
                    key.put("pk", AttributeValue.builder().s(generateKey(index)).build());
                    executor.getItem(tableName, key);
                } else {
                    throw new IllegalArgumentException("Unknown DynamoDB action: " + action);
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Action failed: service=" + workload.service + ", action=" + action);
            System.err.println("Error: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.err
                        .println("Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            throw e;
        }
    }

    private Map<String, AttributeValue> buildItem(int index) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s(generateKey(index)).build());
        int dataLength = workload.intConfig("dataLength");
        item.put("data", AttributeValue.builder().s(randomString(dataLength)).build());
        return item;
    }

    private String generateKey(int index) {
        return workload.stringConfig("keyPrefix") + (index + 1);
    }

    private static String randomString(int length) {
        // Reference runner uses [A-Za-z0-9]; matched here for byte-for-byte
        // compatibility on the wire.
        var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        var random = new Random();
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void printBatchResults(int startIndex, long batchDurationNs) {
        int endIndex = measuredDurationsNs.size();
        if (endIndex <= startIndex) {
            System.out.println("  No operations in this batch");
            return;
        }
        var batch = new ArrayList<>(measuredDurationsNs.subList(startIndex, endIndex));
        Collections.sort(batch);
        int count = batch.size();
        double batchSec = batchDurationNs / 1e9;
        long totalBytes = (long) count * payloadSize;
        double gbps = (totalBytes * 8.0 / batchSec) / 1e9;
        long max = batch.get(count - 1);
        long p50 = batch.get(count / 2);
        long p90 = batch.get((int) (count * 0.9));
        long p99 = batch.get((int) (count * 0.99));
        double avgNs = batch.stream().mapToLong(Long::longValue).average().orElse(0);
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
        if (measuredDurationsNs.isEmpty()) {
            System.out.println("No measurements collected");
            return;
        }
        Collections.sort(measuredDurationsNs);
        int count = measuredDurationsNs.size();
        double totalSec = totalDurationNs / 1e9;
        long totalBytes = (long) count * payloadSize;
        double gbps = (totalBytes * 8.0 / totalSec) / 1e9;
        long min = measuredDurationsNs.get(0);
        long max = measuredDurationsNs.get(count - 1);
        long p50 = measuredDurationsNs.get(count / 2);
        long p90 = measuredDurationsNs.get((int) (count * 0.9));
        long p99 = measuredDurationsNs.get((int) (count * 0.99));
        double avgNs = measuredDurationsNs.stream().mapToLong(Long::longValue).average().orElse(0);
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

    public static void main(String[] args) throws Exception {
        // Allow the JDK HttpClient to set a "host" header before anything
        // creates an HttpRequest.Builder. JavaHttpClientTransport sets this
        // in its static initializer, but we hit a chicken-and-egg problem:
        // the IMDS credential provider builds an HttpClient before
        // JavaHttpClientTransport ever loads, by which time the "host"
        // header restriction is locked in. Setting it here, first thing,
        // sidesteps the whole ordering issue.
        var restricted = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
        if (restricted == null || restricted.isEmpty()) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        } else if (!restricted.contains("host")) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", restricted + ",host");
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
        // Mirror the reference runner's CLI:
        //   java -jar runner.jar [--client sync|async] <workload-file> <region>
        // We only support sync (smithy-java has no async client). The
        // <region> CLI argument is accepted but ignored — region is read
        // from the workload's actionConfig like every other field.
        int idx = 0;
        if (args.length > 0 && "--client".equals(args[0])) {
            if (args.length < 2) {
                fail("Error: --client requires a value (sync or async)");
            }
            String mode = args[1];
            if ("async".equals(mode)) {
                System.err.println("WARNING: smithy-java does not generate async clients. "
                        + "Running with the synchronous client; throughput tests will use a thread pool.");
            } else if (!"sync".equals(mode)) {
                fail("Error: Invalid client mode '" + mode + "'. Valid values are: sync, async");
            }
            idx = 2;
        }
        if (args.length - idx < 2) {
            fail("Usage: java -jar smithy-java-e2e-benchmark-runner.jar [--client sync|async] <workload-file> <region>");
        }
        var workloadPath = args[idx];
        // args[idx + 1] is the region from the orchestration script; we
        // intentionally ignore it (the workload JSON is the source of truth).
        var workload = WorkloadConfig.load(workloadPath);
        printActiveJsonProvider();
        new WorkloadRunner(workload).run();
    }

    private static void printActiveJsonProvider() {
        // Reflectively read JsonSettings.PROVIDER so we can confirm which
        // implementation actually got picked, without depending on
        // package-private getters.
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
