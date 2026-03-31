/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.utils.AbstractCodegenFileTest;
import software.amazon.smithy.model.node.ObjectNode;

public class JSpecifyAnnotationTest extends AbstractCodegenFileTest {
    private static final URL TEST_FILE = Objects.requireNonNull(
            JSpecifyAnnotationTest.class.getResource("jspecify-annotation-test.smithy"));

    @Override
    protected URL testFile() {
        return TEST_FILE;
    }

    @Override
    protected ObjectNode settings() {
        return super.settings().toBuilder()
                .withMember("addNullnessAnnotations", true)
                .build();
    }

    @Test
    void nullMarkedOnStructureClass() {
        var fileStr = getFileStringForClass("JSpecifyAnnotationStructInput");
        assertTrue(fileStr.contains("@NullMarked"));
        assertTrue(fileStr.contains("import org.jspecify.annotations.NullMarked;"));
    }

    @Test
    void nullableOnOptionalGetter() {
        var fileStr = getFileStringForClass("JSpecifyAnnotationStructInput");
        assertTrue(fileStr.contains("public @Nullable String getOptionalString()"));
        assertTrue(fileStr.contains("import org.jspecify.annotations.Nullable;"));
    }

    @Test
    void nullableOnOptionalSetter() {
        var fileStr = getFileStringForClass("JSpecifyAnnotationStructInput");
        assertTrue(fileStr.contains("public Builder optionalString(@Nullable String optionalString)"));
    }

    @Test
    void noNullableOnRequiredGetter() {
        var fileStr = getFileStringForClass("JSpecifyAnnotationStructInput");
        assertTrue(fileStr.contains("public RequiredStruct getRequiredStruct()"));
        assertFalse(fileStr.contains("@Nullable RequiredStruct"));
    }

    @Test
    void noNullableOnPrimitive() {
        var fileStr = getFileStringForClass("JSpecifyAnnotationStructInput");
        assertTrue(fileStr.contains("public boolean isRequiredPrimitive()"));
    }

    @Test
    void noNullableOnPrivateFields() {
        var fileStr = getFileStringForClass("JSpecifyAnnotationStructInput");
        // Private nullable field should use boxed type without @Nullable
        assertTrue(fileStr.contains("private final transient String optionalString;"));
        assertFalse(fileStr.contains("@Nullable String optionalString;"));
    }

    @Test
    void nullMarkedOnUnion() {
        var fileStr = getFileStringForClass("TestUnion");
        assertTrue(fileStr.contains("@NullMarked"));
    }

    @Test
    void nullMarkedOnEnum() {
        var fileStr = getFileStringForClass("TestEnum");
        assertTrue(fileStr.contains("@NullMarked"));
    }

    @Test
    void sparseListHasNullableElementType() {
        var fileStr = getFileStringForClass("AdditionalJSpecifyTestsInput");
        // The collection getter should return List<@Nullable String> (not @Nullable List)
        assertTrue(fileStr.contains("List<@Nullable String>"));
    }

    @Test
    void noNonNullAnnotation() {
        var fileStr = getFileStringForClass("JSpecifyAnnotationStructInput");
        assertFalse(fileStr.contains("@NonNull"));
    }

    @Test
    void nullableOnOptionalPrimitiveGetter() {
        var fileStr = getFileStringForClass("JSpecifyAnnotationStructInput");
        // Optional primitive (Integer) should be boxed and @Nullable on getter
        assertTrue(fileStr.contains("public @Nullable Integer getOptionalPrimitive()"));
    }

    @Test
    void nullableOnOptionalPrimitiveSetter() {
        var fileStr = getFileStringForClass("JSpecifyAnnotationStructInput");
        assertTrue(fileStr.contains("public Builder optionalPrimitive(@Nullable Integer optionalPrimitive)"));
    }

    @Test
    void sparseMapHasNullableValueType() {
        var fileStr = getFileStringForClass("AdditionalJSpecifyTestsInput");
        // Sparse map should have @Nullable only on value, not key: Map<String, @Nullable String>
        assertTrue(fileStr.contains("Map<String, @Nullable String>"));
    }
}
