/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.client.DynamoDBClient;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.AttributeValue;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.GetItemInput;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.model.PutItemInput;
import software.amazon.smithy.java.benchmarks.e2e.s3.client.S3Client;
import software.amazon.smithy.java.benchmarks.e2e.s3.model.GetObjectInput;
import software.amazon.smithy.java.benchmarks.e2e.s3.model.PutObjectInput;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Wraps the smithy-java DynamoDB and S3 clients with the four operations the
 * benchmark exercises. Smithy-java currently only generates synchronous
 * clients, so the throughput tests achieve concurrency by submitting work to
 * a thread pool from {@link WorkloadRunner}.
 */
final class ActionExecutor {

    private final DynamoDBClient ddb;
    private final S3Client s3;
    private final byte[] payload;

    ActionExecutor(DynamoDBClient ddb, S3Client s3, byte[] payload) {
        this.ddb = ddb;
        this.s3 = s3;
        this.payload = payload;
    }

    void putItem(String tableName, Map<String, AttributeValue> item) {
        ddb.putItem(PutItemInput.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    void getItem(String tableName, Map<String, AttributeValue> key) {
        ddb.getItem(GetItemInput.builder()
                .tableName(tableName)
                .key(key)
                .build());
    }

    void putObject(String bucket, String key, int objectSize) {
        // The reference runner reuses a single in-memory body across all
        // uploads. DataStream.ofBytes wraps the array without copying, so
        // each call is a thin handle over the same buffer.
        var body = DataStream.ofBytes(payload, 0, objectSize, "application/octet-stream");
        s3.putObject(PutObjectInput.builder()
                .bucket(bucket)
                .key(key)
                .contentLength((long) objectSize)
                .body(body)
                .build());
    }

    void getObject(String bucket, String key) {
        var output = s3.getObject(GetObjectInput.builder()
                .bucket(bucket)
                .key(key)
                .build());
        // Drain the body so the download time is what we measure, not just
        // the response headers. writeTo(nullOutputStream) walks the stream
        // without forcing a single-contiguous-ByteBuffer materialization, so
        // we exercise the SDK's most efficient consume path (multi-chunk
        // delivery from the wire is fed straight through with no stitch).
        var body = output.getBody();
        if (body != null) {
            try {
                body.writeTo(OutputStream.nullOutputStream());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to drain S3 GetObject body", e);
            }
        }
    }

}
