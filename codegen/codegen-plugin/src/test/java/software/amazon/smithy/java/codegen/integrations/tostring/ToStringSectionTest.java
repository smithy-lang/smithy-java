/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.tostring;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.nio.file.Paths;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.java.codegen.JavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Verifies that {@link software.amazon.smithy.java.codegen.sections.ToStringSection} lets an integration
 * replace the generated {@code toString()} of structures and union variants.
 *
 * <p>The replacement is done by {@link ToStringSectionIntegration}, discovered via {@code ServiceLoader}.
 */
public class ToStringSectionTest {

    private final MockManifest manifest = new MockManifest();

    @BeforeEach
    public void setup() {
        var model = Model.assembler()
                .addImport(Objects.requireNonNull(ToStringSectionTest.class.getResource("to-string-section.smithy")))
                .assemble()
                .unwrap();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("namespace", ToStringSectionIntegration.MARKER_NAMESPACE)
                        .withMember("modes", ArrayNode.fromStrings("types"))
                        .build())
                .model(model)
                .build();
        new JavaCodegenPlugin().execute(context);
    }

    @Test
    void replacesStructureToString() {
        var source = source("Plain.java");

        assertThat(source, containsString("return \"custom:smithy.java.codegen.tostring#Plain\";"));
        assertThat(source, not(containsString("SchemaUtils.toString(this)")));
    }

    @Test
    void replacesEachUnionVariantToString() {
        var source = source("Choice.java");

        assertThat(source, containsString("return \"custom:smithy.java.codegen.tostring#Choice$text\";"));
        assertThat(source, containsString("return \"custom:smithy.java.codegen.tostring#Choice$count\";"));
        assertThat(source, not(containsString("SchemaUtils.toString(this)")));
    }

    private String source(String fileName) {
        return manifest.getFiles()
                .stream()
                .map(Object::toString)
                .filter(p -> p.endsWith("/" + fileName))
                .findFirst()
                .flatMap(p -> manifest.getFileString(Paths.get(p)))
                .orElseThrow(() -> new AssertionError(fileName + " not found in " + manifest.getFiles()));
    }
}
