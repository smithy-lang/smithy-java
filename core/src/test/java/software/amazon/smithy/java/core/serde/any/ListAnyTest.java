/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.any;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class ListAnyTest {

    @Test
    public void createsAnyWithoutSchema() {
        List<Any> values = List.of(Any.of(1), Any.of(2));
        var any = Any.of(values);

        assertThat(any.type(), equalTo(ShapeType.LIST));
        assertThat(any.schema().id(), equalTo(Any.SCHEMA.id()));
        assertThat(any.asList(), equalTo(values));
        assertThat(any, equalTo(Any.of(values)));
    }

    @Test
    public void validatesSchemaType() {
        Assertions.assertThrows(SdkSerdeException.class, () -> {
            Any.of(List.of(Any.of(1)), SdkSchema.builder().id("smithy.example#Shape").type(ShapeType.BYTE).build());
        });
    }

    @Test
    public void createsAnyWithSchema() {
        SdkSchema stringShape = SdkSchema.builder()
            .type(ShapeType.STRING)
            .id(ShapeId.from("smithy.api#String"))
            .build();
        ShapeId listShapeId = ShapeId.from("smithy.example#ListShape");
        SdkSchema listMember = SdkSchema.memberBuilder(0, "member", stringShape)
            .id(listShapeId)
            .build();
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id(listShapeId)
            .members(listMember)
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(ShapeId.from("smithy.example#Foo"))
            .build();
        List<Any> values = List.of(Any.of("a", listMember), Any.of("b", listMember));
        var any = Any.of(values, schema);

        assertThat(any.type(), equalTo(ShapeType.LIST));
        assertThat(any.schema(), equalTo(schema));
        assertThat(any.asList(), equalTo(values));
        assertThat(any, equalTo(Any.of(values, schema)));
    }

    @Test
    public void validatesListMemberSchemaConsistency() {
        SdkSchema stringShape = SdkSchema.builder()
            .type(ShapeType.STRING)
            .id(ShapeId.from("smithy.api#String"))
            .build();
        SdkSchema integerShape = SdkSchema.builder()
            .type(ShapeType.INTEGER)
            .id(ShapeId.from("smithy.api#Integer"))
            .build();
        ShapeId listShapeId = ShapeId.from("smithy.example#ListShape");
        SdkSchema listMember1 = SdkSchema.memberBuilder(0, "member", stringShape)
            .id(listShapeId)
            .build();
        SdkSchema listMember2 = SdkSchema.memberBuilder(0, "member", integerShape)
            .id(listShapeId)
            .build();
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id(listShapeId)
            .members(listMember1)
            .build();
        var schema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(ShapeId.from("smithy.example#Foo"))
            .build();
        List<Any> values = List.of(Any.of("a", listMember1), Any.of(1, listMember2));

        var e = Assertions.assertThrows(SdkSerdeException.class, () -> Any.of(values, schema));

        assertThat(e.getMessage(), containsString("""
            Every member of a list Any must use the same exact Schema. Expected element 1 of the list to be \
            SdkSchema{id='smithy.example#ListShape$member', type=string}, but found \
            SdkSchema{id='smithy.example#ListShape$member', type=integer} in the list for \
            SdkSchema{id='smithy.example#Foo$mymember', type=list}"""));
    }

    @Test
    public void serializesShape() {
        SdkSchema stringShape = SdkSchema.builder()
            .type(ShapeType.STRING)
            .id(ShapeId.from("smithy.api#String"))
            .build();
        ShapeId listShapeId = ShapeId.from("smithy.example#ListShape");
        SdkSchema listMember = SdkSchema.memberBuilder(0, "member", stringShape)
            .id(listShapeId)
            .build();
        var targetSchema = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id(listShapeId)
            .members(listMember)
            .build();
        var listSchema = SdkSchema.memberBuilder(0, "mymember", targetSchema)
            .id(ShapeId.from("smithy.example#Foo"))
            .build();
        List<Any> values = List.of(Any.of("a", listMember), Any.of("b", listMember));
        var any = Any.of(values, listSchema);

        List<String> writtenStrings = new ArrayList<>();

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            protected RuntimeException throwForInvalidState(SdkSchema schema) {
                return new RuntimeException("Unexpected: " + schema);
            }

            @Override
            public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
                assertThat(schema, equalTo(listSchema));
                consumer.accept(new SpecificShapeSerializer() {
                    @Override
                    protected RuntimeException throwForInvalidState(SdkSchema schema) {
                        return new RuntimeException("Unexpected list member: " + schema);
                    }

                    @Override
                    public void writeString(SdkSchema schema, String value) {
                        assertThat(schema, equalTo(listMember));
                        writtenStrings.add(value);
                    }
                });
            }
        };

        any.serialize(serializer);

        assertThat(writtenStrings, contains("a", "b"));
    }
}
