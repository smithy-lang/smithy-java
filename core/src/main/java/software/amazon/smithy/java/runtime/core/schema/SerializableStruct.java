/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

/**
 * A structure or union shape.
 */
public interface SerializableStruct extends SerializableShape {

    @Override
    default void serialize(ShapeSerializer encoder) {
        encoder.writeStruct(schema(), this);
    }

    /**
     * Get the schema of the shape.
     *
     * @return the schema.
     */
    SdkSchema schema();

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
    static SerializableStruct create(SdkSchema schema, BiConsumer<SdkSchema, ShapeSerializer> memberWriter) {
        return new SerializableStruct() {
            @Override
            public SdkSchema schema() {
                return schema;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                memberWriter.accept(schema, serializer);
            }
        };
    }
}
