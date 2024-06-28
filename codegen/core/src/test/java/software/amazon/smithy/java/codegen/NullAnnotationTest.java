/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.utils.AbstractCodegenFileTest;
import software.amazon.smithy.model.node.ObjectNode;

public class NullAnnotationTest extends AbstractCodegenFileTest {
    private static final URL TEST_FILE = Objects.requireNonNull(
        NullAnnotationTest.class.getResource("null-annotation-test.smithy")
    );

    @Override
    protected URL testFile() {
        return TEST_FILE;
    }

    @Override
    protected ObjectNode settings() {
        return super.settings().toBuilder()
            .withMember("nullAnnotation", "software.amazon.smithy.java.codegen.utils.TestNonNullAnnotation")
            .build();
    }

    @Test
    void nullAnnotationsOnFieldsAndGetter() {
        var fileStr = getFileStringForClass("NonNullAnnotationStructInput");
        var expectedField = "private transient final @TestNonNullAnnotation RequiredStruct requiredStruct;";
        var expectedGetter = "public @TestNonNullAnnotation RequiredStruct requiredStruct()";
        var expectedImport = "import software.amazon.smithy.java.codegen.utils.TestNonNullAnnotation;";
        var expectedToString = "public @TestNonNullAnnotation String toString() {";

        assertTrue(fileStr.contains(expectedField));
        assertTrue(fileStr.contains(expectedGetter));
        assertTrue(fileStr.contains(expectedImport));
        assertTrue(fileStr.contains(expectedToString));
    }

    @Test
    void nullAnnotationNotAddedForPrimitive() {
        var fileStr = getFileStringForClass("NonNullAnnotationStructInput");

        var expectedField = "private transient final boolean requiredPrimitive;";
        var expectedGetter = "public boolean requiredPrimitive() {";
        var expectedToString = "public @TestNonNullAnnotation String toString() {";

        assertTrue(fileStr.contains(expectedField));
        assertTrue(fileStr.contains(expectedGetter));
    }

    @Test
    void nullAnnotationAddedForUnionVariant() {
        var fileStr = getFileStringForClass("TestUnion");
        var expectedGetterBoxed = "public @TestNonNullAnnotation String boxedVariant() {";
        var expectedGetterPrimitive = "public @TestNonNullAnnotation String primitiveVariant() {";
        var expectedToString = "public @TestNonNullAnnotation String toString() {";
        var expectedTypeGetter = "public @TestNonNullAnnotation Type type() {";

        assertTrue(fileStr.contains(expectedGetterBoxed));
        assertTrue(fileStr.contains(expectedGetterPrimitive));
        assertTrue(fileStr.contains(expectedToString));
        assertTrue(fileStr.contains(expectedTypeGetter));
    }

    @Test
    void nullAnnotationAddedForEnumVariant() {
        var fileStr = getFileStringForClass("TestEnum");
        var expectedValueGetter = "public @TestNonNullAnnotation String value() {";
        var expectedValueField = "private final @TestNonNullAnnotation String value;";
        var expectedToString = "public @TestNonNullAnnotation String toString() {";
        var expectedTypeGetter = "public @TestNonNullAnnotation Type type() {";

        assertTrue(fileStr.contains(expectedValueGetter));
        assertTrue(fileStr.contains(expectedValueField));
        assertTrue(fileStr.contains(expectedToString));
        assertTrue(fileStr.contains(expectedTypeGetter));
    }
}
