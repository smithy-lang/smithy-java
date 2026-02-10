/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.util.List;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;

class TestStructs {

    /**
     * Mock implementation of SerializableStruct for testing.
     */
    static class MockStruct implements SerializableStruct {

        @Override
        public Schema schema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    static class MockApiOperation implements ApiOperation<MockStruct, MockStruct> {

        private final Schema schema;

        MockApiOperation(String name) {
            this.schema = Schema.createOperation(ShapeId.from("mock#" + name));
        }

        @Override
        public ShapeBuilder inputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder outputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema schema() {
            return schema;
        }

        @Override
        public Schema inputSchema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema outputSchema() {
            return null;
        }

        @Override
        public TypeRegistry errorRegistry() {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }
    }

    static Operation<MockStruct, MockStruct> createMockOperation(String name) {
        return Operation.<MockStruct, MockStruct>of(
                name,
                (input, context) -> input,
                new MockApiOperation(name),
                null);
    }
}
