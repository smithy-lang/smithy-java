/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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

final class DdbBenchmarks extends BenchmarkSupport {

    private static final Duration DEFAULT_DDB_WAITER_TIMEOUT = Duration.ofMinutes(5);

    private final AttributeValue dataValue;

    DdbBenchmarks(BenchmarkConfig config) {
        super(config, switch (config.operation()) {
            case DDB_PUT -> config.dataLength();
            case DDB_GET -> 1024;
            default -> throw new IllegalStateException("Unsupported DynamoDB operation: " + config.operation());
        });
        this.dataValue = AttributeValue.builder().s("x".repeat(config.dataLength())).build();
    }

    @Override
    void run() {
        switch (config.operation()) {
            case DDB_PUT -> runPutItem();
            case DDB_GET -> runGetItem();
            default -> throw new IllegalStateException("Unsupported DynamoDB operation: " + config.operation());
        }
    }

    private void runPutItem() {
        var client = Clients.dynamodb(config.region());
        try (var table = DdbTable.setup(client, config)) {
            var executor = new ActionExecutor(client, null, new byte[0]);
            runMeasured(index -> executor.putItem(table.name(), buildItem(index)));
        }
    }

    private void runGetItem() {
        var client = Clients.dynamodb(config.region());
        try (var table = DdbTable.setup(client, config)) {
            var executor = new ActionExecutor(client, null, new byte[0]);
            if (table.created()) {
                System.out.println("Seeding DynamoDB GetItem keys: " + config.batchActions());
                for (int i = 0; i < config.batchActions(); i++) {
                    executor.putItem(table.name(), buildItem(i));
                }
            }
            runMeasured(index -> {
                var pk = AttributeValue.builder().s(ddbKey(index)).build();
                executor.getItem(table.name(), Map.of("pk", pk));
            });
        }
    }

    private Map<String, AttributeValue> buildItem(int index) {
        var pk = AttributeValue.builder().s(ddbKey(index)).build();
        return Map.of("pk", pk, "data", dataValue);
    }

    private String ddbKey(int index) {
        return config.keyPrefix() + (index + 1);
    }

    private record DdbTable(DynamoDBClient client, String name, boolean created, boolean deleteOnClose)
            implements AutoCloseable {

        private static DdbTable setup(DynamoDBClient client, BenchmarkConfig config) {
            if (!config.ddbCreateTable()) {
                System.out.println("Using existing DynamoDB table: " + config.tableName());
                return new DdbTable(client, config.tableName(), false, false);
            }

            var tableName = uniqueTableName(config.tableName(), config.operation().id);
            System.out.println("Creating DynamoDB table: " + tableName
                    + " (PROVISIONED "
                    + config.ddbReadCapacityUnits() + " RCU / "
                    + config.ddbWriteCapacityUnits() + " WCU)");
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
                            .readCapacityUnits(config.ddbReadCapacityUnits())
                            .writeCapacityUnits(config.ddbWriteCapacityUnits())
                            .build())
                    .build());
            var describe = describeInput(tableName);
            client.waiter().tableExists().wait(describe, DEFAULT_DDB_WAITER_TIMEOUT);
            System.out.println("DynamoDB table active: " + tableName);
            return new DdbTable(client, tableName, true, config.ddbDeleteTable());
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
                e.printStackTrace(System.err);
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
