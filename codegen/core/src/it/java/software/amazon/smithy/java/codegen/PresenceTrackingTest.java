/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smithy.codegen.test.model.BooleansInput;
import io.smithy.codegen.test.model.Nested;
import io.smithy.codegen.test.model.StructuresInput;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;

public class PresenceTrackingTest {
    @Test
    void throwsSerdeExceptionOnMissingRequiredForPrimitiveField() {
        var exc = assertThrows(SerializationException.class, () -> {
            // Missing requiredBooleanField
            BooleansInput.builder().optionalBoolean(true).build();
        });
        assertTrue(exc.getMessage().contains("requiredBoolean"));
    }

    @Test
    void throwsSerdeExceptionOnMissingRequiredForNonPrimitiveField() {
        var exc = assertThrows(SerializationException.class, () -> {
            // Missing requiredBooleanField
            StructuresInput.builder().optionalStruct(Nested.builder().build()).build();
        });
        assertTrue(exc.getMessage().contains("requiredStruct"));
    }
}
