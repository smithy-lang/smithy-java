/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.any;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class TimestampAnyTest {

    private Instant getTestTime() {
        LocalDate date = LocalDate.of(2006, 3, 1);
        return date.atStartOfDay(ZoneId.of("UTC")).toInstant();
    }

    @Test
    public void createsAnyWithoutSchema() {
        var time = getTestTime();
        var any = Any.of(time);

        assertThat(any.type(), equalTo(ShapeType.TIMESTAMP));
        assertThat(any.schema().id(), equalTo(Any.SCHEMA.id()));
        assertThat(any.asTimestamp(), equalTo(time));
        assertThat(any, equalTo(Any.of(time)));
    }

    @Test
    public void validatesSchemaType() {
        Assertions.assertThrows(SdkSerdeException.class, () -> {
            Any.of(getTestTime(), SdkSchema.builder().id("smithy.example#Shape").type(ShapeType.BYTE).build());
        });
    }

    @Test
    public void createsAnyWithSchema() {
        var time = getTestTime();
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.TIMESTAMP)
            .id(ShapeId.from("smithy.example#Shape"))
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(targetSchema.id())
            .build();
        var any = Any.of(time, schema);

        assertThat(any.type(), equalTo(ShapeType.TIMESTAMP));
        assertThat(any.schema(), equalTo(schema));
        assertThat(any.asTimestamp(), equalTo(time));
        assertThat(any, equalTo(Any.of(time, schema)));
    }

    @Test
    public void serializesShape() {
        var time = getTestTime();
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.TIMESTAMP)
            .id(ShapeId.from("smithy.example#Shape"))
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(targetSchema.id())
            .build();

        var any = Any.of(time, schema);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            protected RuntimeException throwForInvalidState(SdkSchema schema) {
                return new RuntimeException("Unexpected " + schema);
            }

            @Override
            public void writeTimestamp(SdkSchema schema, Instant value) {
                assertThat(schema, equalTo(schema));
                assertThat(value, equalTo(time));
            }
        };

        any.serialize(serializer);
    }
}
