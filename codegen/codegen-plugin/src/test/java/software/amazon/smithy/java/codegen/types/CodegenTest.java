/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.java.codegen.JavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;

public class CodegenTest {
    private static final URL testFile = Objects.requireNonNull(CodegenTest.class.getResource("types.smithy"));
    private static final Model model = Model.assembler()
            .addImport(testFile)
            .assemble()
            .unwrap();

    private final SmithyBuildPlugin plugin = new JavaCodegenPlugin();
    private MockManifest manifest;
    private PluginContext.Builder contextBuilder;
    private ObjectNode.Builder settingsBuilder;

    @BeforeEach
    public void setup() {
        manifest = new MockManifest();
        contextBuilder = PluginContext.builder()
                .fileManifest(manifest)
                .model(model);
        settingsBuilder = ObjectNode.builder()
                .withMember("namespace", "test.smithy.codegen.types.test")
                .withMember("modes", ArrayNode.fromStrings("types"));
    }

    @Test
    void expectedFilesExist() {
        var settings = settingsBuilder.build();
        var context = contextBuilder.settings(settings).build();
        plugin.execute(context);
        assertThat(manifest.getFiles())
                .hasSize(8)
                .containsExactlyInAnyOrder(
                        Path.of("/java/test/smithy/codegen/types/test/model/EnumShape.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/IntEnumShape.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/Schemas.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/SharedSerde.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/StructureShape.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/UnionShape.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/GeneratedSchemaIndex.java"),
                        Path.of("/resources/META-INF/services/software.amazon.smithy.java.core.schema.SchemaIndex"));
    }

    @Test
    void respectsSelector() {
        var settings = settingsBuilder
                .withMember("selector", ":is(structure)")
                .build();
        var context = contextBuilder.settings(settings).build();
        plugin.execute(context);
        assertThat(manifest.getFiles())
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        Path.of("/java/test/smithy/codegen/types/test/model/Schemas.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/SharedSerde.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/StructureShape.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/GeneratedSchemaIndex.java"),
                        Path.of("/resources/META-INF/services/software.amazon.smithy.java.core.schema.SchemaIndex"));
    }

    @Test
    void specificShapesAdded() {
        var settings = settingsBuilder
                .withMember("selector", ":is(structure)")
                .withMember("shapes", ArrayNode.fromStrings("smithy.java.codegen.types.test#UnionShape"))
                .build();
        var context = contextBuilder.settings(settings).build();
        plugin.execute(context);
        assertEquals(6, manifest.getFiles().size());
        assertThat(manifest.getFiles())
                .hasSize(6)
                .containsExactlyInAnyOrder(
                        Path.of("/java/test/smithy/codegen/types/test/model/Schemas.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/SharedSerde.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/StructureShape.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/UnionShape.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/GeneratedSchemaIndex.java"),
                        Path.of("/resources/META-INF/services/software.amazon.smithy.java.core.schema.SchemaIndex"));
    }

    @Test
    void emptySelectorFailsLoudly() {
        // A selector that matches no shapes (here, operations in a data-only model) must fail fast
        // rather than silently generating nothing.
        var settings = settingsBuilder
                .withMember("selector", ":is(operation)")
                .build();
        var context = contextBuilder.settings(settings).build();
        assertThatThrownBy(() -> plugin.execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("no shapes");
    }

    @Test
    void appliesRenames() {
        var renamed = Path.of("/java/test/smithy/codegen/types/test/model/RenamedStructure.java");
        var original = Path.of("/java/test/smithy/codegen/types/test/model/StructureShape.java");
        var settings = settingsBuilder
                .withMember("selector", ":is(structure)")
                .withMember("renames",
                        ObjectNode.builder()
                                .withMember("smithy.java.codegen.types.test#StructureShape", "RenamedStructure")
                                .build())
                .build();
        var context = contextBuilder.settings(settings).build();
        plugin.execute(context);
        // The renamed shape produces the renamed class file, and the original name is gone.
        assertThat(manifest.getFiles()).contains(renamed).doesNotContain(original);
        assertThat(manifest.expectFileString(renamed)).contains("public final class RenamedStructure");
    }

    @Test
    void generatesFromAuthoredClosure() {
        // The model authors a `shapeClosures` entry that selects only StructureShape and renames it.
        // Referencing it by id drives generation off that closure instead of an inline selector.
        var closureModel = Model.assembler()
                .addImport(Objects.requireNonNull(CodegenTest.class.getResource("authored-closure.smithy")))
                .assemble()
                .unwrap();
        var settings = settingsBuilder
                .withMember("closure", "smithy.java.codegen.types.test#authoredClosure")
                .build();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .model(closureModel)
                .settings(settings)
                .build();
        plugin.execute(context);

        var renamed = Path.of("/java/test/smithy/codegen/types/test/model/AuthoredStructure.java");
        assertThat(manifest.getFiles())
                // Only the single closure member is generated, under its closure-defined rename.
                .contains(renamed)
                .doesNotContain(Path.of("/java/test/smithy/codegen/types/test/model/StructureShape.java"))
                .doesNotContain(Path.of("/java/test/smithy/codegen/types/test/model/UnionShape.java"));
        assertThat(manifest.expectFileString(renamed)).contains("public final class AuthoredStructure");
    }

    @Test
    void closureCannotBeCombinedWithInlineSettings() {
        var settings = settingsBuilder
                .withMember("closure", "smithy.java.codegen.types.test#authoredClosure")
                .withMember("selector", ":is(structure)")
                .build();
        var context = contextBuilder.settings(settings).build();
        assertThatThrownBy(() -> plugin.execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("cannot be combined with the inline");
    }

}
