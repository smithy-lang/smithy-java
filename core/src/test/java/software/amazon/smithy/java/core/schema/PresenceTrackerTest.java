/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static software.amazon.smithy.java.core.schema.ValidatorTest.createBigRequiredSchema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.core.serde.SerializationException;

public class PresenceTrackerTest {

    @Test
    void validatesInlineRequiredMemberBitfield() {
        var schema = createBigRequiredSchema(3, 3, 0);

        var exception = assertThrows(
                SerializationException.class,
                () -> PresenceTracker.validateRequiredMembers(schema, 0b101L));
        assertEquals("Missing required members: [member1]", exception.getMessage());
        assertDoesNotThrow(() -> PresenceTracker.validateRequiredMembers(
                schema,
                schema.requiredStructureMemberBitfield()));
    }

    @Test
    void sortsMissingInlineRequiredMembersByName() {
        var schema = createBigRequiredSchema(12, 12, 0);
        long setMembers = schema.requiredStructureMemberBitfield() & ~((1L << 2) | (1L << 10));

        var exception = assertThrows(
                SerializationException.class,
                () -> PresenceTracker.validateRequiredMembers(schema, setMembers));

        assertEquals("Missing required members: [member10, member2]", exception.getMessage());
    }

    @Test
    void rejectsOversizedInlineRequiredMemberBitfield() {
        var schema = createBigRequiredSchema(65, 65, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> PresenceTracker.validateRequiredMembers(schema, 0L));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 63, 64, 65, 128})
    void throwsUnsetMembers(int requiredFields) {
        var exc = assertThrows(
                SerializationException.class,
                () -> PresenceTracker.of(createBigRequiredSchema(requiredFields, requiredFields, 0)).validate());
        for (var i = 0; i < requiredFields; i++) {
            assertTrue(exc.getMessage().contains("member" + i));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 63, 64, 65, 128})
    void presenceTrackerType(int requiredFields) {
        Class<?> expected;
        if (requiredFields == 0) {
            expected = PresenceTracker.NoOpPresenceTracker.class;
        } else if (requiredFields <= 64) {
            expected = PresenceTracker.RequiredMemberPresenceTracker.class;
        } else {
            expected = PresenceTracker.BigRequiredMemberPresenceTracker.class;
        }

        assertEquals(
                expected,
                PresenceTracker.of(createBigRequiredSchema(requiredFields, requiredFields, 0)).getClass());
    }
}
