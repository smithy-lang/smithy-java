/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Front door for the fixed live AWS e2e benchmarks.
 */
public final class WorkloadRunner {

    private WorkloadRunner() {}

    public static void main(String[] args) {
        configureRuntime();
        printActiveJsonProvider();

        var config = BenchmarkConfig.parse(args);
        if (config.operation().isS3()) {
            new S3Benchmarks(config).run();
        } else {
            new DdbBenchmarks(config).run();
        }
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
}


enum Operation {
    S3_PUT("s3-put", false),
    S3_GET("s3-get", false),
    DDB_PUT("ddb-put", true),
    DDB_GET("ddb-get", true);

    final String id;
    final boolean sequential;

    Operation(String id, boolean sequential) {
        this.id = id;
        this.sequential = sequential;
    }

    boolean isS3() {
        return this == S3_PUT || this == S3_GET;
    }

    boolean isDdb() {
        return this == DDB_PUT || this == DDB_GET;
    }

    static Operation parse(String value) {
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


record BenchmarkConfig(
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
        long ddbWriteCapacityUnits) {
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
