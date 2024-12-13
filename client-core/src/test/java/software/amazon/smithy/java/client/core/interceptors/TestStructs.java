/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.interceptors;

import java.util.List;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;

public final class TestStructs {

    private TestStructs() {}

    static final ApiOperation<Foo, Foo> OPERATION = new ApiOperation<>() {
        @Override
        public ShapeBuilder<Foo> inputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<Foo> outputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema schema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema inputSchema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema outputSchema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeRegistry typeRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ShapeId> effectiveAuthSchemes() {
            return List.of();
        }
    };

    static final class Foo implements SerializableStruct {
        @Override
        public Schema schema() {
            return PreludeSchemas.DOCUMENT;
        }

        @Override
        public void serialize(ShapeSerializer encoder) {
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

    static final class Bar implements SerializableStruct {
        @Override
        public Schema schema() {
            return PreludeSchemas.DOCUMENT;
        }

        @Override
        public void serialize(ShapeSerializer encoder) {
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
}
