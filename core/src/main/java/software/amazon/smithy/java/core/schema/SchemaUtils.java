/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

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
        try {
            if (member == parent.members().get(member.memberIndex())) {
                return value;
            }
        } catch (IndexOutOfBoundsException ignored) {}
        throw illegalMemberAccess(parent, member);
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

}
