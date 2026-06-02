/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.Random;

final class S3Benchmarks extends BenchmarkSupport {

    private final byte[] payload;

    S3Benchmarks(BenchmarkConfig config) {
        super(config, config.objectSize());
        this.payload = new byte[Math.max(config.objectSize(), 1)];
        new Random(0xC0FFEEL).nextBytes(payload);
    }

    @Override
    void run() {
        switch (config.operation()) {
            case S3_PUT -> runPutObject();
            case S3_GET -> runGetObject();
            default -> throw new IllegalStateException("Unsupported S3 operation: " + config.operation());
        }
    }

    private void runPutObject() {
        var executor = new ActionExecutor(null, Clients.s3(config.region()), payload);
        runMeasured(index -> executor.putObject(config.bucketName(), s3Key(index), config.objectSize()));
    }

    private void runGetObject() {
        var executor = new ActionExecutor(null, Clients.s3(config.region()), payload);
        runMeasured(index -> executor.getObject(config.bucketName(), s3Key(index)));
    }

    private String s3Key(int index) {
        return config.s3KeyPrefix() + (index + 1);
    }
}
