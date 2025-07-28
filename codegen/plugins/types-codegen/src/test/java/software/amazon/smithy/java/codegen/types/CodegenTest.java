/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;

public class CodegenTest {
    private static final URL testFile = Objects.requireNonNull(CodegenTest.class.getResource("types.smithy"));
    private static final Model model = Model.assembler()
            .addImport(testFile)
            .assemble()
            .unwrap();

    private final SmithyBuildPlugin plugin = new JavaTypeCodegenPlugin();
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
                .withMember("namespace", "test.smithy.codegen.types.test");
    }

    @Test
    void expectedFilesExist() {
        var settings = settingsBuilder.build();
        var context = contextBuilder.settings(settings).build();
        plugin.execute(context);
        assertThat(manifest.getFiles())
                .hasSize(9)
                .containsExactlyInAnyOrder(
                        Path.of("/test/smithy/codegen/types/test/model/EnumShape.java"),
                        Path.of("/test/smithy/codegen/types/test/model/IntEnumShape.java"),
                        Path.of("/test/smithy/codegen/types/test/model/Schemas.java"),
                        Path.of("/test/smithy/codegen/types/test/model/SharedSerde.java"),
                        Path.of("/test/smithy/codegen/types/test/model/StructureShape.java"),
                        Path.of("/test/smithy/codegen/types/test/model/UnionShape.java"),
                        Path.of("/test/smithy/codegen/types/test/model/GeneratedSchemaIndex.java"),
                        Path.of("/META-INF/smithy-java/type-mappings.properties"),
                        Path.of("/META-INF/services/software.amazon.smithy.java.core.schema.SchemaIndex"));
    }

    @Test
    void respectsSelector() {
        var settings = settingsBuilder
                .withMember("selector", ":is(structure)")
                .build();
        var context = contextBuilder.settings(settings).build();
        plugin.execute(context);
        assertThat(manifest.getFiles())
                .hasSize(6)
                .containsExactlyInAnyOrder(
                        Path.of("/test/smithy/codegen/types/test/model/Schemas.java"),
                        Path.of("/test/smithy/codegen/types/test/model/SharedSerde.java"),
                        Path.of("/test/smithy/codegen/types/test/model/StructureShape.java"),
                        Path.of("/test/smithy/codegen/types/test/model/GeneratedSchemaIndex.java"),
                        Path.of("/META-INF/smithy-java/type-mappings.properties"),
                        Path.of("/META-INF/services/software.amazon.smithy.java.core.schema.SchemaIndex"));
    }

    @Test
    void specificShapesAdded() {
        var settings = settingsBuilder
                .withMember("selector", ":is(structure)")
                .withMember("shapes", ArrayNode.fromStrings("smithy.java.codegen.types.test#UnionShape"))
                .build();
        var context = contextBuilder.settings(settings).build();
        plugin.execute(context);
        assertEquals(7, manifest.getFiles().size());
        assertThat(manifest.getFiles())
                .hasSize(7)
                .containsExactlyInAnyOrder(
                        Path.of("/test/smithy/codegen/types/test/model/Schemas.java"),
                        Path.of("/test/smithy/codegen/types/test/model/SharedSerde.java"),
                        Path.of("/test/smithy/codegen/types/test/model/StructureShape.java"),
                        Path.of("/test/smithy/codegen/types/test/model/UnionShape.java"),
                        Path.of("/test/smithy/codegen/types/test/model/GeneratedSchemaIndex.java"),
                        Path.of("/META-INF/smithy-java/type-mappings.properties"),
                        Path.of("/META-INF/services/software.amazon.smithy.java.core.schema.SchemaIndex"));
    }

}
