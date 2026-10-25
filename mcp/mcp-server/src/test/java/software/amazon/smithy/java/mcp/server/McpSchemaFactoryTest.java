/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.Unit;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;

class McpSchemaFactoryTest {

    @Test
    void toolUsesRuntimeOperationNameInsteadOfSourceSchemaName() {
        var service = new TestService();
        var operation = Operation.of(
                "Echo",
                (input, ignored) -> input,
                new TestApiOperation("EchoProxy"),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        assertEquals("Echo", descriptor.info().getName());
        assertEquals(
                "This tool invokes Echo API of TestService.",
                descriptor.info().getDescription());
    }

    @Test
    void ordinaryToolAlsoUsesItsRuntimeOperationName() {
        var service = new TestService();
        var operation = Operation.of(
                "TestSimpleText",
                (input, ignored) -> input,
                new TestApiOperation("SimpleTextSchema"),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        assertEquals("TestSimpleText", descriptor.info().getName());
    }

    private static final class TestService implements Service {
        private static final Schema SCHEMA =
                Schema.createService(ShapeId.from("example#TestService"));

        @Override
        public <I extends SerializableStruct,
                O extends SerializableStruct> Operation<I, O> getOperation(
                        String operationName
                ) {
            return null;
        }

        @Override
        public List<Operation<? extends SerializableStruct,
                ? extends SerializableStruct>> getAllOperations() {
            return List.of();
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public TypeRegistry typeRegistry() {
            return TypeRegistry.empty();
        }

        @Override
        public SchemaIndex schemaIndex() {
            return SchemaIndex.compose();
        }
    }

    private static final class TestApiOperation implements ApiOperation<Unit, Unit> {
        private static final ApiService SERVICE =
                () -> Schema.createService(ShapeId.from("example#TestService"));
        private final Schema schema;

        private TestApiOperation(String name) {
            schema = Schema.createOperation(ShapeId.from("example#" + name));
        }

        @Override
        public ShapeBuilder<Unit> inputBuilder() {
            return Unit.builder();
        }

        @Override
        public ShapeBuilder<Unit> outputBuilder() {
            return Unit.builder();
        }

        @Override
        public Schema schema() {
            return schema;
        }

        @Override
        public Schema inputSchema() {
            return Unit.SCHEMA;
        }

        @Override
        public Schema outputSchema() {
            return Unit.SCHEMA;
        }

        @Override
        public TypeRegistry errorRegistry() {
            return TypeRegistry.empty();
        }

        @Override
        public List<ShapeId> effectiveAuthSchemes() {
            return List.of();
        }

        @Override
        public List<Schema> errorSchemas() {
            return List.of();
        }

        @Override
        public ApiService service() {
            return SERVICE;
        }
    }
}
