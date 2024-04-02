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

public class FloatAnyTest {

    @Test
    public void createsAnyWithoutSchema() {
        var any = Any.of(1.0f);

        assertThat(any.type(), equalTo(ShapeType.FLOAT));
        assertThat(any.schema().id(), equalTo(Any.SCHEMA.id()));
        assertThat(any.asFloat(), equalTo(1.0f));
        assertThat(any, equalTo(Any.of(1.0f)));
    }

    @Test
    public void validatesSchemaType() {
        Assertions.assertThrows(SdkSerdeException.class, () -> {
            Any.of(1f, SdkSchema.builder().id("smithy.example#Shape").type(ShapeType.BYTE).build());
        });
    }

    @Test
    public void createsAnyWithSchema() {
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.FLOAT)
            .id(ShapeId.from("smithy.example#Shape"))
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(targetSchema.id())
            .build();
        var any = Any.of(1.0f, schema);

        assertThat(any.type(), equalTo(ShapeType.FLOAT));
        assertThat(any.schema(), equalTo(schema));
        assertThat(any.asFloat(), equalTo(1.0f));
        assertThat(any, equalTo(Any.of(1.0f, schema)));
    }

    @Test
    public void serializesShape() {
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.FLOAT)
            .id(ShapeId.from("smithy.example#Shape"))
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(targetSchema.id())
            .build();

        var any = Any.of(1.0f, schema);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            protected RuntimeException throwForInvalidState(SdkSchema schema) {
                return new RuntimeException("Unexpected " + schema);
            }

            @Override
            public void writeFloat(SdkSchema schema, float value) {
                assertThat(schema, equalTo(schema));
                assertThat(value, equalTo(1.0f));
            }
        };

        any.serialize(serializer);
    }

    @Test
    public void canWiden() {
        var any = Any.of(1.0f);

        assertThat(any.asDouble(), equalTo(1.0));
    }
}
