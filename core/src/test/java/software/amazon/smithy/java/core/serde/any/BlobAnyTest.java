/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.any;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class BlobAnyTest {

    @Test
    public void createsAnyWithoutSchema() {
        var any = Any.of("hi".getBytes(StandardCharsets.UTF_8));

        assertThat(any.type(), equalTo(ShapeType.BLOB));
        assertThat(any.schema().id(), equalTo(Any.SCHEMA.id()));
        assertThat(any.asBlob(), equalTo("hi".getBytes(StandardCharsets.UTF_8)));
        assertThat(any, equalTo(Any.of("hi".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void validatesSchemaType() {
        Assertions.assertThrows(SdkSerdeException.class, () -> {
            Any.of(
                "hi".getBytes(StandardCharsets.UTF_8),
                SdkSchema.builder().id("smithy.example#Shape").type(ShapeType.BYTE).build()
            );
        });
    }

    @Test
    public void createsAnyWithSchema() {
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.BLOB)
            .id(ShapeId.from("smithy.example#Shape"))
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(targetSchema.id())
            .build();
        var any = Any.of("hi".getBytes(StandardCharsets.UTF_8), schema);

        assertThat(any.type(), equalTo(ShapeType.BLOB));
        assertThat(any.schema(), equalTo(schema));
        assertThat(any.asBlob(), equalTo("hi".getBytes(StandardCharsets.UTF_8)));
        assertThat(any, equalTo(Any.of("hi".getBytes(StandardCharsets.UTF_8), schema)));
    }

    @Test
    public void serializesShape() {
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.BLOB)
            .id(ShapeId.from("smithy.example#Shape"))
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(targetSchema.id())
            .build();

        var any = Any.of("hi".getBytes(StandardCharsets.UTF_8), schema);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            protected RuntimeException throwForInvalidState(SdkSchema schema) {
                return new RuntimeException("Unexpected " + schema);
            }

            @Override
            public void writeBlob(SdkSchema schema, byte[] value) {
                assertThat(schema, equalTo(schema));
                assertThat(value, equalTo("hi".getBytes(StandardCharsets.UTF_8)));
            }
        };

        any.serialize(serializer);
    }
}
