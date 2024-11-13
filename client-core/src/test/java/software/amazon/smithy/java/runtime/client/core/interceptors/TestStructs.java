/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.interceptors;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

public final class TestStructs {

    private TestStructs() {}

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
        public Object getMemberValue(Schema member) {
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
        public Object getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }
}
