/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.test.model.BooleanMembersInput;
import software.amazon.smithy.java.codegen.test.model.NestedStruct;
import software.amazon.smithy.java.codegen.test.model.StructureMembersInput;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;

public class PresenceTrackingTest {
    @Test
    void throwsSerdeExceptionOnMissingRequiredForPrimitiveField() {
        var exc = assertThrows(SerializationException.class, () -> {
            // Missing requiredBooleanField
            BooleanMembersInput.builder().optionalBoolean(true).build();
        });
        assertTrue(exc.getMessage().contains("requiredBoolean"));
    }

    @Test
    void throwsSerdeExceptionOnMissingRequiredForNonPrimitiveField() {
        var exc = assertThrows(SerializationException.class, () -> {
            // Missing requiredBooleanField
            StructureMembersInput.builder().optionalStruct(NestedStruct.builder().build()).build();
        });
        assertTrue(exc.getMessage().contains("requiredStruct"));
    }
}
