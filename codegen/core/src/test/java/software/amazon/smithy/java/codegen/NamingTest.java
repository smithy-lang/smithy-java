/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.java.codegen.utils.TestJavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

public class NamingTest {
    private static final URL TEST_FILE = Objects.requireNonNull(
        NamingTest.class.getResource("naming-test.smithy")
    );
    private final MockManifest manifest = new MockManifest();

    @BeforeEach
    public void setup() {
        var model = Model.assembler()
            .addImport(TEST_FILE)
            .assemble()
            .unwrap();
        var context = PluginContext.builder()
            .fileManifest(manifest)
            .settings(
                ObjectNode.builder()
                    .withMember("service", "smithy.java.codegen#TestService")
                    .withMember("namespace", "test.smithy.codegen")
                    .build()
            )
            .model(model)
            .build();
        SmithyBuildPlugin plugin = new TestJavaCodegenPlugin();
        plugin.execute(context);

        assertFalse(manifest.getFiles().isEmpty());
    }

    @Test
    void usesFullyQualifiedMap() {
        var fileStr = getFileStringForClass("NamingConflictsInput");
        // Java map uses fully qualified name
        var expectedJavaMapGetter = """
                public java.util.Map<String, String> javaMap() {
            """;
        assertTrue(fileStr.contains(expectedJavaMapGetter));

        // Custom map does not use fully qualified name
        var expectedCustomMap = """
                public Map map() {
                    return map;
                }
            """;
        assertTrue(fileStr.contains(expectedCustomMap));
    }

    @Test
    void usesFullyQualifiedList() {
        var fileStr = getFileStringForClass("NamingConflictsInput");
        // Java map uses fully qualified name
        var expectedJavaListGetter = """
                public java.util.List<String> javaList() {
            """;
        assertTrue(fileStr.contains(expectedJavaListGetter));

        // Custom map does not use fully qualified name
        var expectedCustomList = """
                public List list() {
                    return list;
                }
            """;
        assertTrue(fileStr.contains(expectedCustomList));
    }

    @Test
    void escapesMemberNames() {
        var fileStr = getFileStringForClass("ReservedWordMembersInput");
        var expected = """
                private transient final Byte byteMember;
                private transient final String staticMember;
                private transient final Double doubleMember;
            """;
        assertTrue(fileStr.contains(expected));
    }

    @Test
    void escapesShapeNames() {
        var fileStr = getFileStringForClass("BuilderShape");
        var expected = "public final class BuilderShape";
        assertTrue(fileStr.contains(expected));
    }

    @Test
    void snakeCaseMember() {
        var fileStr = getFileStringForClass("CasingInput");
        // Member schema still uses the raw member name
        assertTrue(fileStr.contains(".memberBuilder(\"snake_case_member\", PreludeSchemas.STRING)"));
        // Member property is renamed to java-friendly string
        assertTrue(fileStr.contains("private transient final String snakeCaseMember;"));
    }

    @Test
    void snakeCaseShape() {
        var fileStr = getFileStringForClass("SnakeCaseShape");
        assertTrue(fileStr.contains("public final class SnakeCaseShape implements SerializableStruct"));
    }

    @Test
    void upperSnakeCaseShape() {
        var fileStr = getFileStringForClass("UpperSnakeCaseShape");
        assertTrue(fileStr.contains("public final class UpperSnakeCaseShape implements SerializableStruct"));
    }

    @Test
    void acronymInsideMember() {
        var fileStr = getFileStringForClass("CasingInput");
        // Member schema still uses the raw member name
        assertTrue(fileStr.contains("memberBuilder(\"ACRONYM_Inside_Member\", PreludeSchemas.STRING)"));
        // Member property is renamed to java-friendly string
        assertTrue(fileStr.contains("private transient final String acronymInsideMember;"));
    }

    @Test
    void acronymInsideStruct() {
        var fileStr = getFileStringForClass("ACRONYMInsideStruct");
        assertTrue(fileStr.contains("public final class ACRONYMInsideStruct implements SerializableStruct"));
    }

    static List<Arguments> enumCaseArgs() {
        return List.of(
            Arguments.of("CAMEL_CASE", "camelCase"),
            Arguments.of("SNAKE_CASE", "snake_case"),
            Arguments.of("PASCAL_CASE", "PascalCase"),
            Arguments.of("WITH_1_NUMBER", "with_1_number")
        );
    }

    @ParameterizedTest
    @MethodSource("enumCaseArgs")
    void enumCasing(String updated, String original) {
        var fileStr = getFileStringForClass("EnumCasing");
        // All variants should maintain the raw value for the value, but convert the type to expected string
        assertTrue(fileStr.contains(String.format("new EnumCasing(Type.%s, \"%s\")", updated, original)));
    }

    private String getFileStringForClass(String className) {
        var fileStringOptional = manifest.getFileString(
            Paths.get(String.format("/test/smithy/codegen/model/%s.java", className))
        );
        assertTrue(fileStringOptional.isPresent());
        return fileStringOptional.get();
    }
}
