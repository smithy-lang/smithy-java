/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import software.amazon.smithy.java.runtime.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

/**
 * A structure or union shape.
 */
public interface SerializableStruct extends SerializableShape {
    /**
     * Serializes the members of the structure or union.
     *
     * @param serializer Serializer to write to.
     */
    void serializeMembers(ShapeSerializer serializer);

    /**
     * Create a serializable struct from a schema and BiConsumer.
     *
     * @param schema Schema of the structure / union.
     * @param memberWriter BiConsumer that writes members to the given serializer.
     * @return the created SerializableStruct.
     */
    static SerializableStruct create(Schema schema, BiConsumer<Schema, ShapeSerializer> memberWriter) {
        return new SerializableStruct() {
            @Override
            public void serialize(ShapeSerializer encoder) {
                encoder.writeStruct(schema, this);
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                memberWriter.accept(schema, serializer);
            }
        };
    }

    /**
     * Create a serializable struct that only serializes members that pass the given predicate.
     *
     * @param schema Structure schema.
     * @param struct Struct to serialize.
     * @param memberPredicate Predicate that takes a member schema.
     * @return the filtered struct.
     */
    static SerializableStruct filteredMembers(
        Schema schema,
        SerializableStruct struct,
        Predicate<Schema> memberPredicate
    ) {
        return new SerializableStruct() {
            @Override
            public void serialize(ShapeSerializer encoder) {
                encoder.writeStruct(schema, this);
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                struct.serializeMembers(new InterceptingSerializer() {
                    @Override
                    protected ShapeSerializer before(Schema schema) {
                        return memberPredicate.test(schema) ? serializer : ShapeSerializer.nullSerializer();
                    }
                });
            }
        };
    }
}
