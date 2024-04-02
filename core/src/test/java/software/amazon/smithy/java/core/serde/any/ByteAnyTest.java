/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.any;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class ByteAnyTest {

    @Test
    public void createsAnyWithoutSchema() {
        var any = Any.of((byte) 1);

        assertThat(any.type(), equalTo(ShapeType.BYTE));
        assertThat(any.schema().id(), equalTo(Any.SCHEMA.id()));
        assertThat(any.asByte(), equalTo((byte) 1));
        assertThat(any, equalTo(Any.of((byte) 1)));
    }

    @Test
    public void validatesSchemaType() {
        Assertions.assertThrows(SdkSerdeException.class, () -> {
            Any.of((byte) 1, SdkSchema.builder().id("smithy.example#Shape").type(ShapeType.SHORT).build());
        });
    }

    @Test
    public void createsAnyWithSchema() {
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.BYTE)
            .id(ShapeId.from("smithy.example#Shape"))
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(targetSchema.id())
            .build();
        var any = Any.of((byte) 1, schema);

        assertThat(any.type(), equalTo(ShapeType.BYTE));
        assertThat(any.schema(), equalTo(schema));
        assertThat(any.asByte(), equalTo((byte) 1));
        assertThat(any, equalTo(Any.of((byte) 1, schema)));
    }

    @Test
    public void serializesShape() {
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.BYTE)
            .id(ShapeId.from("smithy.example#Shape"))
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(targetSchema.id())
            .build();

        var any = Any.of((byte) 1, schema);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            protected RuntimeException throwForInvalidState(SdkSchema schema) {
                return new RuntimeException("Unexpected " + schema);
            }

            @Override
            public void writeByte(SdkSchema schema, byte value) {
                assertThat(schema, equalTo(schema));
                assertThat(value, equalTo((byte) 1));
            }
        };

        any.serialize(serializer);
    }

    @Test
    public void canWiden() {
        var any = Any.of((byte) 1);

        assertThat(any.asByte(), equalTo((byte) 1));
        assertThat(any.asShort(), equalTo((short) 1));
        assertThat(any.asInteger(), equalTo(1));
        assertThat(any.asLong(), equalTo(1L));
        assertThat(any.asFloat(), equalTo(1f));
        assertThat(any.asDouble(), equalTo(1.0));
    }
}
