/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smithy.codegen.test.model.BooleansInput;
import io.smithy.codegen.test.model.ListsInput;
import io.smithy.codegen.test.model.MapsInput;
import io.smithy.codegen.test.model.Nested;
import io.smithy.codegen.test.model.StructuresInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;

public class PresenceTrackingTest {
    @Test
    void throwsSerdeExceptionOnMissingRequiredForPrimitiveField() {
        var exc = assertThrows(SdkSerdeException.class, () -> {
            // Missing requiredBooleanField
            BooleansInput.builder().optionalBoolean(true).build();
        });
        assertTrue(exc.getMessage().contains("requiredBoolean"));
    }

    @Test
    void throwsSerdeExceptionOnMissingRequiredForNonPrimitiveField() {
        var exc = assertThrows(SdkSerdeException.class, () -> {
            // Missing requiredBooleanField
            StructuresInput.builder().optionalStruct(Nested.builder().build()).build();
        });
        assertTrue(exc.getMessage().contains("requiredStruct"));
    }

    @Test
    void tracksListWithMultipleSetCalls() {
        var list = ListsInput.builder()
            .requiredList("one call")
            .requiredList("two calls")
            .build();
        assertEquals(list.requiredList(), List.of("one call", "two calls"));
        assertEquals(list.listWithDefault(), List.of());
        assertFalse(list.hasOptionalList());
    }

    @Test
    void tracksMapWithMultipleSetCalls() {
        var map = MapsInput.builder()
            .putRequiredMap("one call", "a")
            .putRequiredMap("two calls", "b")
            .build();
        assertEquals(map.requiredMap(), Map.of("one call", "a", "two calls", "b"));
        assertEquals(map.defaultMap(), Map.of());
        assertFalse(map.hasOptionalMap());
    }
}
