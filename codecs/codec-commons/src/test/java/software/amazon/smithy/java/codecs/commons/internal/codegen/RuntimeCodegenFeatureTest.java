/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static software.amazon.smithy.java.core.serde.RuntimeCodegenMode.DISABLED;
import static software.amazon.smithy.java.core.serde.RuntimeCodegenMode.ENABLED;
import static software.amazon.smithy.java.core.serde.RuntimeCodegenMode.STRICT;

import org.junit.jupiter.api.Test;

final class RuntimeCodegenFeatureTest {
    private static final String PROPERTY = "smithy-java.runtime-codegen";

    @Test
    void unsetModeFollowsTheGlobalProperty() {
        assertEquals(DISABLED, RuntimeCodegenFeature.resolve(null, "resolve-a"));

        withProperty(PROPERTY,
                "enabled",
                () -> assertEquals(ENABLED, RuntimeCodegenFeature.resolve(null, "resolve-a")));
        withProperty(PROPERTY, "strict", () -> assertEquals(STRICT, RuntimeCodegenFeature.resolve(null, "resolve-a")));
    }

    @Test
    void backendPropertyOverridesTheGlobalProperty() {
        withProperty(PROPERTY, "enabled", () -> {
            withProperty(PROPERTY + ".resolve-b", "strict", () -> {
                assertEquals(STRICT, RuntimeCodegenFeature.resolve(null, "resolve-b"));
                assertEquals(ENABLED, RuntimeCodegenFeature.resolve(null, "resolve-other"));
            });
            withProperty(PROPERTY + ".resolve-b",
                    "disabled",
                    () -> assertEquals(DISABLED, RuntimeCodegenFeature.resolve(null, "resolve-b")));
        });
    }

    @Test
    void explicitModeCombinesWithThePropertyBySeverity() {
        assertEquals(ENABLED, RuntimeCodegenFeature.resolve(ENABLED, "resolve-c"));
        assertEquals(STRICT, RuntimeCodegenFeature.resolve(STRICT, "resolve-c"));
        assertEquals(DISABLED, RuntimeCodegenFeature.resolve(DISABLED, "resolve-c"));

        withProperty(PROPERTY, "strict", () -> {
            assertEquals(STRICT, RuntimeCodegenFeature.resolve(ENABLED, "resolve-c"));
            assertEquals(DISABLED, RuntimeCodegenFeature.resolve(DISABLED, "resolve-c"));
        });
        withProperty(PROPERTY,
                "enabled",
                () -> assertEquals(STRICT, RuntimeCodegenFeature.resolve(STRICT, "resolve-c")));
    }

    @Test
    void disabledPropertyIsAKillSwitchOverExplicitOptIn() {
        withProperty(PROPERTY + ".resolve-d", "disabled", () -> {
            assertEquals(DISABLED, RuntimeCodegenFeature.resolve(ENABLED, "resolve-d"));
            assertEquals(DISABLED, RuntimeCodegenFeature.resolve(STRICT, "resolve-d"));
            assertEquals(ENABLED, RuntimeCodegenFeature.resolve(ENABLED, "resolve-other"));
        });
        withProperty(PROPERTY,
                "disabled",
                () -> assertEquals(DISABLED, RuntimeCodegenFeature.resolve(STRICT, "resolve-d")));
    }

    @Test
    void rejectsAModeThatIsNotAModeName() {
        withProperty(PROPERTY, "json,cbor", () -> {
            var thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> RuntimeCodegenFeature.resolve(null, "resolve-e"));
            assertTrue(thrown.getMessage().contains(PROPERTY));
        });
    }

    private static void withProperty(String property, String value, Runnable body) {
        System.setProperty(property, value);
        try {
            body.run();
        } finally {
            System.clearProperty(property);
        }
    }
}
