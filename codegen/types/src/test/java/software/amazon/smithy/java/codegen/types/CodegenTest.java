/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertFalse(manifest.getFiles().isEmpty());
        assertEquals(manifest.getFiles().size(), 6);
        assertThat(
            manifest.getFiles().stream().map(Path::toString).toList(),
            containsInAnyOrder(
                "/test/smithy/codegen/types/test/model/EnumShape.java",
                "/test/smithy/codegen/types/test/model/IntEnumShape.java",
                "/test/smithy/codegen/types/test/model/SharedSchemas.java",
                "/test/smithy/codegen/types/test/model/SharedSerde.java",
                "/test/smithy/codegen/types/test/model/StructureShape.java",
                "/test/smithy/codegen/types/test/model/UnionShape.java"
            )
        );
    }

    @Test
    void respectsSelector() {
        var settings = settingsBuilder
            .withMember("selector", ":is(structure)")
            .build();
        var context = contextBuilder.settings(settings).build();
        plugin.execute(context);
        assertFalse(manifest.getFiles().isEmpty());
        assertEquals(manifest.getFiles().size(), 3);
        assertThat(
            manifest.getFiles().stream().map(Path::toString).toList(),
            containsInAnyOrder(
                "/test/smithy/codegen/types/test/model/SharedSchemas.java",
                "/test/smithy/codegen/types/test/model/SharedSerde.java",
                "/test/smithy/codegen/types/test/model/StructureShape.java"
            )
        );
    }

    @Test
    void specificShapesAdded() {
        var settings = settingsBuilder
            .withMember("selector", ":is(structure)")
            .withMember("shapes", ArrayNode.fromStrings("smithy.java.codegen.types.test#UnionShape"))
            .build();
        var context = contextBuilder.settings(settings).build();
        plugin.execute(context);
        assertFalse(manifest.getFiles().isEmpty());
        assertEquals(manifest.getFiles().size(), 4);
        assertThat(
            manifest.getFiles().stream().map(Path::toString).toList(),
            containsInAnyOrder(
                "/test/smithy/codegen/types/test/model/SharedSchemas.java",
                "/test/smithy/codegen/types/test/model/SharedSerde.java",
                "/test/smithy/codegen/types/test/model/StructureShape.java",
                "/test/smithy/codegen/types/test/model/UnionShape.java"
            )
        );
    }

}
