/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.function.Predicate;
import software.amazon.smithy.java.runtime.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

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
}
