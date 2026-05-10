/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;

/**
 * SPI for specialized (codegen) JSON serialization/deserialization.
 *
 * <p>When an implementation is on the classpath, it is automatically discovered via ServiceLoader
 * and used to wrap the underlying {@link JsonSerdeProvider} with a fast-path decorator.
 */
public interface SpecializedJsonSerde {
    ByteBuffer trySerialize(SerializableStruct struct, JsonSettings settings);

    <T extends SerializableShape> T tryDeserialize(
            byte[] buf,
            int offset,
            int end,
            ShapeBuilder<T> builder,
            JsonSettings settings
    );

    void warmup(Schema schema, Class<?> shapeClass);
}
