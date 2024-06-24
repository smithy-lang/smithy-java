/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.java.codegen.utils.TestJavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

public class PreludeTraitInitializerTest {
    private static final URL TEST_FILE = Objects.requireNonNull(
        PreludeTraitInitializerTest.class.getResource("prelude-trait-initializer-test.smithy")
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
                    .withMember("service", "smithy.java.codegen.integrations.javadoc#TestService")
                    .withMember("namespace", "test.smithy.codegen")
                    .build()
            )
            .model(model)
            .build();
        SmithyBuildPlugin plugin = new TestJavaCodegenPlugin();
        plugin.execute(context);

        assertFalse(manifest.getFiles().isEmpty());
    }

    static List<String> customInitializersInput() {
        return List.of(
            "new DefaultTrait(Node.from(\"string\"))",
            "LengthTrait.builder().min(10L).build()",
            "RangeTrait.builder().max(new BigDecimal(\"100\")).build()",
            "XmlNamespaceTrait.builder().uri(\"http://foo.com\").build()"
        );
    }

    @ParameterizedTest
    @MethodSource("customInitializersInput")
    void customInitializersCorrectOnInput(String expected) {
        var fileContents = getFileStringForClass("SpecialCasedInput");
        assertTrue(fileContents.contains(expected));
    }

    @Test
    void customInitializerForRetryableCorrect() {
        var fileContents = getFileStringForClass("RetryableError");
        assertTrue(fileContents.contains("RetryableTrait.builder().throttling(false).build()"));
    }

    private String getFileStringForClass(String className) {
        var fileStringOptional = manifest.getFileString(
            Paths.get(String.format("/test/smithy/codegen/model/%s.java", className))
        );
        assertTrue(fileStringOptional.isPresent());
        return fileStringOptional.get();
    }
}
