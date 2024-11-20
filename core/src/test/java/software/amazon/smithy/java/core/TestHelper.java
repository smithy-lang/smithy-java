/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core;

import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

public final class TestHelper {

    private TestHelper() {}

    public static SerializableStruct create(Schema schema, BiConsumer<Schema, ShapeSerializer> memberWriter) {
        return new SerializableStruct() {
            @Override
            public Schema schema() {
                return schema;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                memberWriter.accept(schema, serializer);
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                throw new UnsupportedOperationException("Getting member not supported: " + member);
            }
        };
    }
}
