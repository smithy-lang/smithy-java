/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.function.Predicate;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

public final class SchemaUtils {

    private SchemaUtils() {}

    /**
     * Create a serializable struct that only serializes members that pass the given predicate.
     *
     * @param schema Structure schema.
     * @param struct Struct to serialize.
     * @param memberPredicate Predicate that takes a member schema.
     * @return the filtered struct.
     */
    public static SerializableStruct withFilteredMembers(
        Schema schema,
        SerializableStruct struct,
        Predicate<Schema> memberPredicate
    ) {
        return new SerializableStruct() {
            @Override
            public Schema schema() {
                return schema;
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

            @Override
            public Object getMemberValue(Schema member) {
                return memberPredicate.test(schema()) ? getMemberValue(member) : null;
            }
        };
    }

    /**
     * Ensures that {@code member} is contained in {@code parent}, and if so returns {@code value}.
     *
     * @param parent Parent shape to check.
     * @param member Member to check.
     * @param value  Value to return if valid.
     * @throws IllegalArgumentException if the member is not part of parent.
     * @return the given {@code value}.
     */
    public static Object validateMemberInSchema(Schema parent, Schema member, Object value) {
        try {
            if (member == parent.members().get(member.memberIndex())) {
                return value;
            }
        } catch (IndexOutOfBoundsException ignored) {}
        throw illegalMemberAccess(parent, member);
    }

    private static RuntimeException illegalMemberAccess(Schema parent, Schema member) {
        return new IllegalArgumentException(
            "Attempted to access a non-existent member of " + parent.id() + ": " + member.id()
        );
    }

    /**
     * Validates that {@code actual} is referentially equal to {@code actual} and returns {@code value}.
     *
     * @param expected Expected schema.
     * @param actual   Actual schema.
     * @param value    Value to return if it's a match.
     * @return the value.
     * @param <T> Value kind.
     * @throws IllegalArgumentException if the schemas are not the same.
     */
    public static <T> T validateSameMember(Schema expected, Schema actual, T value) {
        if (expected == actual) {
            return value;
        }
        throw new IllegalArgumentException(
            "Attempted to read or write a non-existent member of " + expected.id()
                + ": " + actual.id()
        );
    }

    /**
     * Attempts to copy the values from a struct into a shape builder.
     *
     * @param source The shape to copy from.
     * @param sink   The builder to copy into.
     * @throws IllegalArgumentException if the two shapes are incompatible and don't use the same schemas.
     */
    public static void copyShape(SerializableStruct source, ShapeBuilder<?> sink) {
        for (var member : source.schema().members()) {
            var value = source.getMemberValue(member);
            if (value != null) {
                sink.setMemberValue(member, value);
            }
        }
    }
}
