/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.live;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import software.amazon.smithy.java.benchmarks.live.dynamodb.client.DynamoDBClient;
import software.amazon.smithy.java.benchmarks.live.dynamodb.model.AttributeValue;
import software.amazon.smithy.java.benchmarks.live.dynamodb.model.GetItemInput;
import software.amazon.smithy.java.benchmarks.live.dynamodb.model.PutItemInput;
import software.amazon.smithy.java.benchmarks.live.s3.client.S3Client;
import software.amazon.smithy.java.benchmarks.live.s3.model.GetObjectInput;
import software.amazon.smithy.java.benchmarks.live.s3.model.PutObjectInput;
import software.amazon.smithy.java.io.datastream.DataStream;

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
        // Reuse the payload buffer to match the reference runner.
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
        var body = output.getBody();
        if (body != null) {
            try {
                body.discard();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to discard S3 GetObject body", e);
            }
        }
    }
}
