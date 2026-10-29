/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import software.amazon.smithy.java.core.serde.ToStringSerializer;

public final class SchemaUtils {

    private SchemaUtils() {}

    /**
     * Ensures that {@code member} is contained in {@code parent}, and if so returns {@code value}.
     *
     * @param parent Parent shape to check.
     * @param member Member to check.
     * @param value  Value to return if valid.
     * @throws IllegalArgumentException if the member is not part of parent.
     * @return the given {@code value}.
     */
    public static <T> T validateMemberInSchema(Schema parent, Schema member, T value) {
        validateMemberOf(parent, member);
        return value;
    }

    private static RuntimeException illegalMemberAccess(Schema parent, Schema member) {
        return new IllegalArgumentException(
                "Attempted to access a non-existent member of " + parent.id() + ": " + member.id());
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
                        + ": " + actual.id());
    }

    /**
     * Validates that {@code member} is a member schema of {@code parent}.
     *
     * <p>This is the check behind {@link #validateMemberInSchema}; use it when there is no value to pass through.
     *
     * @throws IllegalArgumentException if the member does not belong to the parent.
     */
    public static void validateMemberOf(Schema parent, Schema member) {
        var members = parent.members();
        int index = member.memberIndex();
        if (index < 0 || index >= members.size() || members.get(index) != member) {
            throw illegalMemberAccess(parent, member);
        }
    }

    /**
     * Creates an {@link IllegalArgumentException} for an unknown member schema.
     */
    public static RuntimeException unknownMember(Schema member) {
        return new IllegalArgumentException("Unknown member: " + member.id());
    }

    /**
     * Formats a shape with {@link ToStringSerializer}.
     */
    public static String toString(SerializableShape shape) {
        return ToStringSerializer.serialize(shape);
    }

    /**
     * Returns the member value if it is not null.
     *
     * @param member Schema of the member being set.
     * @throws NullPointerException if the value is null.
     */
    public static <T> T requireNonNull(T value, Schema member) {
        if (value == null) {
            throw new NullPointerException(member.memberName() + " cannot be null");
        }
        return value;
    }

}
