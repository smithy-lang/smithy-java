/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.any;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.model.shapes.ShapeType;

public class MapAnyTest {

    @Test
    public void ensuresMapKeysAreValidDocuments() {
        Map<Any, Any> entries = Map.of(Any.of(true), Any.of(true));

        var e = Assertions.assertThrows(SdkSerdeException.class, () -> Any.of(entries));

        assertThat(
            e.getMessage(),
            containsString(
                "Map keys must be a string, enum, integer, intEnum, or long, but "
                    + "found BooleanAny"
            )
        );
    }

    @Test
    public void ensuresMapKeyMembersAreNamedKey() {
        var stringSchema = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
        var keyMember = SdkSchema.memberBuilder(0, "member", stringSchema).id("smithy.example#Foo").build();
        Map<Any, Any> entries = Map.of(Any.of("a", keyMember), Any.of(true));

        var e = Assertions.assertThrows(SdkSerdeException.class, () -> Any.of(entries));

        assertThat(
            e.getMessage(),
            containsString(
                "Map Any key member has a member name of 'member', but map key "
                    + "members must be named 'key': StringAny"
            )
        );
    }

    @Test
    public void ensuresMapKeySchemasAreConsistent() {
        var stringSchema = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
        var keyMember = SdkSchema.memberBuilder(0, "key", stringSchema).id("smithy.example#Foo").build();
        Map<Any, Any> entries = Map.of(
            Any.of("a", keyMember),
            Any.of(1),
            Any.of("b"),
            Any.of(2)
        );

        var e = Assertions.assertThrows(SdkSerdeException.class, () -> Any.of(entries));

        assertThat(e.getMessage(), containsString("Every Any map key member must use the same schema."));
    }

    @Test
    public void ensureMapValueMembersNamedValue() {
        var stringSchema = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
        var valueMember = SdkSchema.memberBuilder(0, "foo", stringSchema).id("smithy.example#Foo").build();
        Map<Any, Any> entries = Map.of(Any.of("a"), Any.of("a", valueMember));

        var e = Assertions.assertThrows(SdkSerdeException.class, () -> Any.of(entries));

        assertThat(
            e.getMessage(),
            containsString(
                "Map Any value member has a member name of 'foo', but map value "
                    + "members must be named 'value'"
            )
        );
    }

    @Test
    public void ensureMapValueSchemasAreConsistent() {
        var stringSchema = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
        var valueMember = SdkSchema.memberBuilder(0, "value", stringSchema).id("smithy.example#Foo").build();
        Map<Any, Any> entries = Map.of(
            Any.of("a"),
            Any.of("a", valueMember),
            Any.of("b"),
            Any.of("b")
        );

        var e = Assertions.assertThrows(SdkSerdeException.class, () -> Any.of(entries));

        assertThat(e.getMessage(), containsString("Every Any map value member must use the same schema."));
    }

    @Test
    public void createsAnyWithoutSchema() {
        Map<Any, Any> entries = Map.of(
            Any.of("a"),
            Any.of(true),
            Any.of("b"),
            Any.of(false)
        );
        var map = Any.of(entries);

        assertThat(map.type(), is(ShapeType.MAP));
        assertThat(map.schema().id(), equalTo(Any.SCHEMA.id()));
        assertThat(map.asMap(), equalTo(entries));
        assertThat(Any.of(map.asMap()), equalTo(map));
    }

    @Test
    public void serializesShape() {
        var stringSchema = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
        var integerSchema = SdkSchema.builder().type(ShapeType.INTEGER).id("smithy.api#Integer").build();
        var keyMember = SdkSchema.memberBuilder(0, "key", stringSchema).id("smithy.example#Foo").build();
        var valueMember = SdkSchema.memberBuilder(1, "value", integerSchema).id("smithy.example#Foo").build();
        var mapSchema = SdkSchema.builder()
            .id("smithy.example#Foo")
            .type(ShapeType.MAP)
            .members(keyMember, valueMember)
            .build();

        Map<Any, Any> entries = Map.of(
            Any.of("a", keyMember),
            Any.of(1, valueMember),
            Any.of("b", keyMember),
            Any.of(2, valueMember)
        );
        var map = Any.of(entries, mapSchema);

        var keys = new ArrayList<>();
        map.serialize(new SpecificShapeSerializer() {
            @Override
            protected RuntimeException throwForInvalidState(SdkSchema schema) {
                throw new UnsupportedOperationException("Expected a map: " + schema);
            }

            @Override
            public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
                assertThat(schema, equalTo(mapSchema));
                consumer.accept(new MapSerializer() {
                    @Override
                    public void entry(String key, Consumer<ShapeSerializer> valueSerializer) {
                        keys.add(key);
                        valueSerializer.accept(new SpecificShapeSerializer() {
                            @Override
                            protected RuntimeException throwForInvalidState(SdkSchema schema) {
                                throw new UnsupportedOperationException("Expected an integer value: " + schema);
                            }

                            @Override
                            public void writeInteger(SdkSchema schema, int value) {
                                assertThat(schema, equalTo(valueMember));
                                if (key.equals("a")) {
                                    assertThat(value, is(1));
                                } else {
                                    assertThat(value, is(2));
                                }
                            }
                        });
                    }

                    @Override
                    public void entry(int key, Consumer<ShapeSerializer> valueSerializer) {
                        throw new UnsupportedOperationException("Expected a string key");
                    }

                    @Override
                    public void entry(long key, Consumer<ShapeSerializer> valueSerializer) {
                        throw new UnsupportedOperationException("Expected a string key");
                    }
                });
            }
        });

        assertThat(keys, containsInAnyOrder("a", "b"));
    }
}
