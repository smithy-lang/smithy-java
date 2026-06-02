/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.client.DynamoDBClient;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.AttributeDefinition;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.AttributeValue;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.BillingMode;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.CreateTableInput;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.DeleteTableInput;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.DescribeTableInput;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.KeySchemaElement;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.KeyType;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.ProvisionedThroughput;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.ScalarAttributeType;

/**
 * Direct e2e benchmark runner for the four live AWS workloads we actually run.
 */
public final class WorkloadRunner {

    private static final String DEFAULT_REGION = "us-east-1";
    private static final String DEFAULT_BUCKET = "dowling-bench--use1-az4--x-s3";
    private static final String DEFAULT_TABLE = "benchmark-table";
    private static final String DEFAULT_KEY_PREFIX = "item-";
    private static final String DEFAULT_S3_KEY_PREFIX = "objects/256KiB/";
    private static final int DEFAULT_DDB_ACTIONS = 1000;
    private static final int DEFAULT_S3_ACTIONS = 10000;
    private static final int DEFAULT_WARMUP_BATCHES = 2;
    private static final int DEFAULT_MEASUREMENT_BATCHES = 3;
    private static final int DEFAULT_OBJECT_SIZE = 256 * 1024;
    private static final int DEFAULT_DATA_LENGTH = 1024;
    private static final long DEFAULT_DDB_CAPACITY_UNITS = 5000;
    private static final Duration DEFAULT_DDB_WAITER_TIMEOUT = Duration.ofMinutes(5);

    private final BenchmarkConfig config;
    private final int payloadSize;
    private final byte[] payload;
    private final long[] measuredDurationsNs;
    private final AtomicInteger measuredCount = new AtomicInteger();
    private final ResourceMonitor monitor = new ResourceMonitor();

    private WorkloadRunner(BenchmarkConfig config) {
        this.config = config;
        this.payloadSize = switch (config.operation) {
            case S3_PUT, S3_GET -> config.objectSize;
            case DDB_PUT -> config.dataLength;
            case DDB_GET -> 1024;
        };
        this.payload = new byte[Math.max(payloadSize, 1)];
        new Random(0xC0FFEEL).nextBytes(payload);
        this.measuredDurationsNs = new long[config.measurementBatches * config.batchActions];

        System.out.println("Initialized smithy-java e2e benchmark:");
        System.out.println("  Operation: " + config.operation.id);
        System.out.println("  Region:    " + config.region);
        if (config.operation.isS3()) {
            System.out.println("  Bucket:    " + config.bucketName);
            System.out.println("  Object:    " + config.objectSize + " bytes");
        } else {
            System.out.println("  Table:     " + config.tableName);
        }
        System.out.println("  Sequential:        " + config.operation.sequential);
        System.out.println("  Actions per batch: " + config.batchActions);
    }

    private void run() {
        switch (config.operation) {
            case S3_PUT -> runS3Put();
            case S3_GET -> runS3Get();
            case DDB_PUT -> runDdbPut();
            case DDB_GET -> runDdbGet();
        }
    }

    private void runS3Put() {
        var executor = new ActionExecutor(null, Clients.s3(config.region), payload);
        runMeasured(index -> executor.putObject(config.bucketName, s3Key(index), config.objectSize));
    }

    private void runS3Get() {
        var executor = new ActionExecutor(null, Clients.s3(config.region), payload);
        runMeasured(index -> executor.getObject(config.bucketName, s3Key(index)));
    }

    private void runDdbPut() {
        var client = Clients.dynamodb(config.region);
        try (var table = DdbTable.setup(client, config)) {
            var executor = new ActionExecutor(client, null, payload);
            runMeasured(index -> executor.putItem(table.name(), buildItem(index)));
        }
    }

    private void runDdbGet() {
        var client = Clients.dynamodb(config.region);
        try (var table = DdbTable.setup(client, config)) {
            var executor = new ActionExecutor(client, null, payload);
            if (table.created()) {
                System.out.println("Seeding DynamoDB GetItem keys: " + config.batchActions);
                for (int i = 0; i < config.batchActions; i++) {
                    executor.putItem(table.name(), buildItem(i));
                }
            }
            runMeasured(index -> {
                var pk = AttributeValue.builder().s(ddbKey(index)).build();
                executor.getItem(table.name(), Map.of("pk", pk));
            });
        }
    }

    private void runMeasured(Action action) {
        try (ExecutorService pool = config.operation.sequential ? null : Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("\n=== WARMUP PHASE ===");
            for (int i = 0; i < config.warmupBatches; i++) {
                System.out.println("Warmup batch " + (i + 1) + "/" + config.warmupBatches);
                executeBatch(pool, action, false);
            }
            measuredCount.set(0);

            System.out.println("\n=== MEASUREMENT PHASE ===");
            if (config.collectMetrics) {
                monitor.start(config.metricsIntervalMs);
            }

            long startNs = System.nanoTime();
            int lastSampleCount = 0;
            for (int i = 0; i < config.measurementBatches; i++) {
                System.out.println("\nMeasurement batch " + (i + 1) + "/" + config.measurementBatches);
                int operationsBefore = measuredCount.get();
                long batchStart = System.nanoTime();
                executeBatch(pool, action, true);
                long batchDuration = System.nanoTime() - batchStart;
                printBatchResults(operationsBefore, batchDuration);

                if (config.collectMetrics) {
                    int now = monitor.sampleCount();
                    monitor.statsSnapshot(lastSampleCount).printCompact("  Resource Usage: ");
                    lastSampleCount = now;
                }
            }
            long totalDuration = System.nanoTime() - startNs;

            if (config.collectMetrics) {
                monitor.stop().print();
            }

            System.out.println("\n=== OVERALL RESULTS ===");
            printOverall(totalDuration);
        }
    }

    private void executeBatch(ExecutorService pool, Action action, boolean measure) {
        if (config.operation.sequential) {
            for (int i = 0; i < config.batchActions; i++) {
                long start = System.nanoTime();
                action.run(i);
                if (measure) {
                    recordDuration(System.nanoTime() - start);
                }
            }
            return;
        }

        int concurrency = Runtime.getRuntime().availableProcessors()
                * Integer.getInteger("e2e.concurrency.multiplier", 4);
        var permits = new Semaphore(concurrency);
        var done = new CountDownLatch(config.batchActions);
        var error = new AtomicReference<Throwable>();
        for (int i = 0; i < config.batchActions; i++) {
            permits.acquireUninterruptibly();
            final int index = i;
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

    private Map<String, AttributeValue> buildItem(int index) {
        var pk = AttributeValue.builder().s(ddbKey(index)).build();
        var data = AttributeValue.builder().s(randomString(config.dataLength)).build();
        return Map.of("pk", pk, "data", data);
    }

    private String ddbKey(int index) {
        return config.keyPrefix + (index + 1);
    }

    private String s3Key(int index) {
        return config.s3KeyPrefix + (index + 1);
    }

    private static String randomString(int length) {
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

    public static void main(String[] args) {
        configureRuntime();
        printActiveJsonProvider();
        new WorkloadRunner(BenchmarkConfig.parse(args)).run();
    }

    private static void configureRuntime() {
        var restricted = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
        if (restricted == null || restricted.isEmpty()) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        } else if (!restricted.contains("host")) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", restricted + ",host");
        }
        if (System.getProperty("jdk.httpclient.bufsize") == null) {
            System.setProperty("jdk.httpclient.bufsize", "65536");
        }
        if (System.getProperty("jdk.httpclient.maxframesize") == null) {
            System.setProperty("jdk.httpclient.maxframesize", "65536");
        }
        if (System.getProperty("smithy-java.json-provider") == null) {
            System.setProperty("smithy-java.json-provider", "smithy");
        }
        if (!"true".equals(System.getProperty("smithy.bench.debug"))) {
            LogManager.getLogManager().reset();
            Logger.getLogger("").setLevel(java.util.logging.Level.WARNING);
        }
    }

    private static void printActiveJsonProvider() {
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

    private enum Operation {
        S3_PUT("s3-put", false),
        S3_GET("s3-get", false),
        DDB_PUT("ddb-put", true),
        DDB_GET("ddb-get", true);

        private final String id;
        private final boolean sequential;

        Operation(String id, boolean sequential) {
            this.id = id;
            this.sequential = sequential;
        }

        private boolean isS3() {
            return this == S3_PUT || this == S3_GET;
        }

        private static Operation parse(String value) {
            var normalized = value.toLowerCase().replace('_', '-');
            return switch (normalized) {
                case "s3-put", "s3-upload", "putobject", "upload" -> S3_PUT;
                case "s3-get", "s3-download", "getobject", "download" -> S3_GET;
                case "ddb-put", "ddb-putitem", "dynamodb-putitem", "putitem" -> DDB_PUT;
                case "ddb-get", "ddb-getitem", "dynamodb-getitem", "getitem" -> DDB_GET;
                default -> inferFromLegacyPath(normalized);
            };
        }

        private static Operation inferFromLegacyPath(String value) {
            if (value.contains("s3-upload")) {
                return S3_PUT;
            } else if (value.contains("s3-download")) {
                return S3_GET;
            } else if (value.contains("ddb-putitem")) {
                return DDB_PUT;
            } else if (value.contains("ddb-getitem")) {
                return DDB_GET;
            }
            throw new IllegalArgumentException("Unknown benchmark operation: " + value);
        }
    }

    private record BenchmarkConfig(
            Operation operation,
            String region,
            String bucketName,
            String tableName,
            String keyPrefix,
            String s3KeyPrefix,
            int objectSize,
            int dataLength,
            int batchActions,
            int warmupBatches,
            int measurementBatches,
            boolean collectMetrics,
            int metricsIntervalMs,
            boolean ddbCreateTable,
            boolean ddbDeleteTable,
            long ddbReadCapacityUnits,
            long ddbWriteCapacityUnits
    ) {
        static BenchmarkConfig parse(String[] args) {
            String operation = null;
            Map<String, String> flags = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--operation", "--workload" -> operation = requireValue(args, ++i, args[i - 1]);
                    case "--client" -> validateClientMode(requireValue(args, ++i, "--client"));
                    case "--bucket" -> flags.put("bucket", requireValue(args, ++i, "--bucket"));
                    case "--table" -> flags.put("table", requireValue(args, ++i, "--table"));
                    case "--region" -> flags.put("region", requireValue(args, ++i, "--region"));
                    case "--key-prefix" -> flags.put("keyPrefix", requireValue(args, ++i, "--key-prefix"));
                    case "--s3-key-prefix" -> flags.put("s3KeyPrefix", requireValue(args, ++i, "--s3-key-prefix"));
                    default -> {
                        if (args[i].startsWith("--")) {
                            throw new IllegalArgumentException("Unknown flag: " + args[i]);
                        }
                        if (operation != null) {
                            throw new IllegalArgumentException("Unexpected positional argument: " + args[i]);
                        }
                        operation = args[i];
                    }
                }
            }
            if (operation == null) {
                throw new IllegalArgumentException(
                        "Usage: java -jar smithy-java-e2e-benchmark-runner.jar "
                                + "--operation s3-put|s3-get|ddb-put|ddb-get [--bucket <name>] "
                                + "[--table <name>] [--region <region>]");
            }

            var op = Operation.parse(operation);
            int defaultActions = op.isS3() ? DEFAULT_S3_ACTIONS : DEFAULT_DDB_ACTIONS;
            return new BenchmarkConfig(
                    op,
                    flags.getOrDefault("region", System.getProperty("e2e.region", DEFAULT_REGION)),
                    flags.getOrDefault("bucket", System.getProperty("e2e.bucket", DEFAULT_BUCKET)),
                    flags.getOrDefault("table", System.getProperty("e2e.table", DEFAULT_TABLE)),
                    flags.getOrDefault("keyPrefix", System.getProperty("e2e.keyPrefix", DEFAULT_KEY_PREFIX)),
                    flags.getOrDefault("s3KeyPrefix", System.getProperty("e2e.s3KeyPrefix", DEFAULT_S3_KEY_PREFIX)),
                    Integer.getInteger("e2e.object.size", DEFAULT_OBJECT_SIZE),
                    Integer.getInteger("e2e.data.length", DEFAULT_DATA_LENGTH),
                    Integer.getInteger("e2e.batch.actions", defaultActions),
                    Integer.getInteger("e2e.warmup.batches", DEFAULT_WARMUP_BATCHES),
                    Integer.getInteger("e2e.measurement.batches", DEFAULT_MEASUREMENT_BATCHES),
                    Boolean.parseBoolean(System.getProperty("e2e.collectMetrics", "true")),
                    Integer.getInteger("e2e.metrics.interval.ms", 100),
                    Boolean.parseBoolean(System.getProperty("e2e.ddb.createTable", "true")),
                    Boolean.parseBoolean(System.getProperty("e2e.ddb.deleteTable", "true")),
                    Long.getLong("e2e.ddb.readCapacityUnits", DEFAULT_DDB_CAPACITY_UNITS),
                    Long.getLong("e2e.ddb.writeCapacityUnits", DEFAULT_DDB_CAPACITY_UNITS));
        }

        private static void validateClientMode(String mode) {
            if ("async".equals(mode)) {
                System.err.println("WARNING: smithy-java does not generate async clients. Running synchronous client.");
            } else if (!"sync".equals(mode)) {
                throw new IllegalArgumentException("Invalid client mode: " + mode);
            }
        }

        private static String requireValue(String[] args, int i, String flag) {
            if (i >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[i];
        }
    }

    @FunctionalInterface
    private interface Action {
        void run(int index);
    }

    private record DdbTable(DynamoDBClient client, String name, boolean created, boolean deleteOnClose) implements AutoCloseable {

        private static DdbTable setup(DynamoDBClient client, BenchmarkConfig config) {
            if (!config.ddbCreateTable) {
                System.out.println("Using existing DynamoDB table: " + config.tableName);
                return new DdbTable(client, config.tableName, false, false);
            }

            var tableName = uniqueTableName(config.tableName, config.operation.id);
            System.out.println("Creating DynamoDB table: " + tableName
                    + " (PROVISIONED "
                    + config.ddbReadCapacityUnits + " RCU / "
                    + config.ddbWriteCapacityUnits + " WCU)");
            client.createTable(CreateTableInput.builder()
                    .tableName(tableName)
                    .attributeDefinitions(List.of(AttributeDefinition.builder()
                            .attributeName("pk")
                            .attributeType(ScalarAttributeType.S)
                            .build()))
                    .keySchema(List.of(KeySchemaElement.builder()
                            .attributeName("pk")
                            .keyType(KeyType.HASH)
                            .build()))
                    .billingMode(BillingMode.PROVISIONED)
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(config.ddbReadCapacityUnits)
                            .writeCapacityUnits(config.ddbWriteCapacityUnits)
                            .build())
                    .build());
            var describe = describeInput(tableName);
            client.waiter().tableExists().wait(describe, DEFAULT_DDB_WAITER_TIMEOUT);
            System.out.println("DynamoDB table active: " + tableName);
            return new DdbTable(client, tableName, true, config.ddbDeleteTable);
        }

        @Override
        public void close() {
            if (!deleteOnClose) {
                return;
            }
            System.out.println("Deleting DynamoDB table: " + name);
            try {
                client.deleteTable(DeleteTableInput.builder()
                        .tableName(name)
                        .build());
                client.waiter().tableNotExists().wait(describeInput(name), DEFAULT_DDB_WAITER_TIMEOUT);
            } catch (RuntimeException e) {
                System.err.println("WARNING: failed to delete DynamoDB table " + name + ": " + e);
            }
        }

        private static DescribeTableInput describeInput(String tableName) {
            return DescribeTableInput.builder()
                    .tableName(tableName)
                    .build();
        }

        private static String uniqueTableName(String baseName, String operation) {
            var suffix = "-" + operation + "-" + Long.toUnsignedString(System.currentTimeMillis(), 36);
            var maxBaseLength = 255 - suffix.length();
            var prefix = baseName.length() > maxBaseLength ? baseName.substring(0, maxBaseLength) : baseName;
            return prefix + suffix;
        }
    }
}
