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
        assertThat(manifest.getFiles().stream().map(p -> p.getFileName().toString()))
                .filteredOn(name -> name.startsWith("$HiddenEnum_") || name.startsWith("$HiddenIntEnum_"))
                .hasSize(2);
        assertThat(manifest.getFiles())
                .hasSize(9)
                .contains(
                        Path.of("/java/test/smithy/codegen/types/test/model/EnumShape.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/IntEnumShape.java"),
                        Path.of("/java/test/smithy/codegen/types/test/model/Schemas.java"),
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
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        Path.of("/java/test/smithy/codegen/types/test/model/Schemas.java"),
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
        assertEquals(5, manifest.getFiles().size());
        assertThat(manifest.getFiles())
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        Path.of("/java/test/smithy/codegen/types/test/model/Schemas.java"),
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

    @Test
    void inlinesPresenceTrackingForUpTo64RequiredMembers() {
        var source = generateStructureSource(requiredStructure("InlinePresence", 64), "InlinePresence.java");

        assertThat(source)
                .contains("private long $setMembers;")
                .contains("$setMembers |= 0x8000000000000000L;")
                .contains("if ($setMembers != 0xffffffffffffffffL)")
                .contains("PresenceTracker.validateRequiredMembers($SCHEMA, $setMembers);")
                .doesNotContain("private final PresenceTracker tracker");
    }

    @Test
    void retainsPresenceTrackerForMoreThan64RequiredMembers() {
        var source = generateStructureSource(requiredStructure("TrackedPresence", 65), "TrackedPresence.java");

        assertThat(source)
                .contains("private final PresenceTracker tracker = PresenceTracker.of($SCHEMA);")
                .contains("tracker.validate();")
                .contains("if (tracker.allSet())")
                .contains("if (!tracker.checkMember($0))")
                .doesNotContain("private long $setMembers;");
    }

    @Test
    void premarksRequiredClientOptionalMembers() {
        var source = generateStructureSource(
                """
                        $version: "2"
                        namespace smithy.java.codegen.presence

                        structure ClientOptionalPresence {
                            @required
                            @clientOptional
                            value: String
                        }
                        """,
                "ClientOptionalPresence.java");

        assertThat(source)
                .contains("private Builder() {")
                .contains("$setMembers |= 0x1L;")
                .contains("if ($setMembers != 0x1L)");
    }

    @Test
    void usesTypedCollectionWritersAndGeneratedDeserialization() {
        var generatedModel = Model.assembler()
                .addUnparsedModel(
                        "serde.smithy",
                        """
                                $version: "2"
                                namespace smithy.java.codegen.serde

                                list StringList {
                                    member: String
                                }

                                map StringMap {
                                    key: String
                                    value: String
                                }

                                structure Container {
                                    list: StringList
                                    map: StringMap
                                }

                                union Choice {
                                    list: StringList
                                }
                                """)
                .assemble()
                .unwrap();
        var settings = settingsBuilder.build();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .model(generatedModel)
                .settings(settings)
                .build();
        plugin.execute(context);

        assertThat(getGeneratedSource("Container.java"))
                .contains("serializer.writeStringList($0, list, $0.listMember());")
                .contains("serializer.writeStringMap($1, map, $1.mapKeyMember(), $1.mapValueMember());")
                .contains("case 0 -> builder.list(SharedSerde.deserializeStringList(member, de));")
                .contains("case 1 -> builder.map(SharedSerde.deserializeStringMap(member, de));")
                .contains("decoder.readStruct($SCHEMA, this, Builder::$deserializeMember);")
                .doesNotContain("StringListSerializer")
                .doesNotContain("class $InnerDeserializer");
        assertThat(getGeneratedSource("SharedSerde.java"))
                .contains("static void deserializeStringListMember(")
                .contains("static void deserializeStringMapValue(")
                .doesNotContain("class StringMapSerializer")
                .doesNotContain("class StringListSerializer");
        assertThat(getGeneratedSource("Choice.java"))
                .contains("decoder.readStruct($SCHEMA, this, $InnerDeserializer.INSTANCE);")
                .contains("Builder.$deserializeMember(builder, member, de);")
                .contains("builder.$unknownMember(memberName);");
    }

    @Test
    void generatesDeserializersForStructureCollections() {
        var generatedModel = Model.assembler()
                .addUnparsedModel(
                        "serde.smithy",
                        """
                                $version: "2"
                                namespace smithy.java.codegen.serde

                                structure Item {
                                    value: String
                                }

                                list ItemList {
                                    member: Item
                                }

                                map ItemMap {
                                    key: String
                                    value: Item
                                }

                                structure Container {
                                    list: ItemList
                                    map: ItemMap
                                }
                                """)
                .assemble()
                .unwrap();
        var settings = settingsBuilder.build();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .model(generatedModel)
                .settings(settings)
                .build();
        plugin.execute(context);

        assertThat(getGeneratedSource("Container.java"))
                .contains("serializer.writeStructList($0, list, $0.listMember());")
                .contains("serializer.writeStructMap($1, map, $1.mapKeyMember(), $1.mapValueMember());")
                .contains("case 0 -> builder.list(SharedSerde.deserializeItemList(member, de));")
                .contains("case 1 -> builder.map(SharedSerde.deserializeItemMap(member, de));");
        assertThat(getGeneratedSource("SharedSerde.java"))
                .contains("static void deserializeItemListMember(")
                .contains("static void deserializeItemMapValue(")
                .contains("Item.builder().deserializeMember(deserializer, Schemas.ITEM_LIST.listMember()).build()")
                .doesNotContain("class ItemMapSerializer")
                .doesNotContain("class ItemListSerializer");
    }

    @Test
    void generatesSerializerClassesForCollectionsWithoutTypedWriters() {
        var generatedModel = Model.assembler()
                .addUnparsedModel(
                        "serde.smithy",
                        """
                                $version: "2"
                                namespace smithy.java.codegen.serde

                                enum Color {
                                    RED
                                }

                                @sparse
                                list SparseStringList {
                                    member: String
                                }

                                list NestedList {
                                    member: SparseStringList
                                }

                                map ColorMap {
                                    key: Color
                                    value: String
                                }

                                @sparse
                                map SparseListMap {
                                    key: String
                                    value: SparseStringList
                                }

                                structure Container {
                                    nested: NestedList
                                    colors: ColorMap
                                    lists: SparseListMap
                                    sparse: SparseStringList
                                }
                                """)
                .assemble()
                .unwrap();
        var settings = settingsBuilder.build();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .model(generatedModel)
                .settings(settings)
                .build();
        plugin.execute(context);

        assertThat(getGeneratedSource("Container.java"))
                .contains("serializer.writeList($0, nested, nested.size(), SharedSerde.NestedListSerializer.INSTANCE);")
                .contains("serializer.writeMap($1, colors, colors.size(), SharedSerde.ColorMapSerializer.INSTANCE);")
                .contains("serializer.writeMap($2, lists, lists.size(), SharedSerde.SparseListMapSerializer.INSTANCE);")
                .contains("serializer.writeSparseStringList($3, sparse, $3.listMember());");
        assertThat(getGeneratedSource("SharedSerde.java"))
                .contains("class NestedListSerializer implements")
                .contains("class ColorMapSerializer implements")
                .contains("class SparseListMapSerializer implements")
                .contains("serializer.writeSparseStringList($m, value, $m.listMember());")
                .contains("serializer.writeSparseStringList(Schemas.SPARSE_LIST_MAP.mapValueMember(), values, "
                        + "Schemas.SPARSE_LIST_MAP.mapValueMember().listMember());")
                .doesNotContain("class SparseStringListSerializer");
    }

    @Test
    void shardsSerdeUsingSchemaPartitions() {
        var modelSource = new StringBuilder()
                .append("$version: \"2\"\n")
                .append("namespace smithy.java.codegen.serde\n\n")
                .append("structure Item {\n    value: String\n}\n\n");
        for (int i = 0; i < 500; i++) {
            modelSource.append("list Values")
                    .append(i)
                    .append(" {\n    member: Item\n}\n\n");
        }
        modelSource.append("structure Root {\n");
        for (int i = 0; i < 500; i++) {
            modelSource.append("    values")
                    .append(i)
                    .append(": Values")
                    .append(i)
                    .append('\n');
        }
        modelSource.append("}\n");
        var generatedModel = Model.assembler()
                .addUnparsedModel("sharded-serde.smithy", modelSource.toString())
                .assemble()
                .unwrap();
        var settings = settingsBuilder.build();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .model(generatedModel)
                .settings(settings)
                .build();
        plugin.execute(context);

        assertThat(manifest.getFiles().stream().map(path -> path.getFileName().toString()))
                .contains("Schemas.java", "Schemas1.java", "SharedSerde.java", "SharedSerde1.java");
        assertThat(getGeneratedSource("SharedSerde1.java"))
                .contains("Schemas1.")
                .doesNotContain("Schemas.");
    }

    private String generateStructureSource(String modelSource, String fileName) {
        var generatedModel = Model.assembler()
                .addUnparsedModel("presence.smithy", modelSource)
                .assemble()
                .unwrap();
        var settings = settingsBuilder.build();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .model(generatedModel)
                .settings(settings)
                .build();
        plugin.execute(context);
        return getGeneratedSource(fileName);
    }

    private String getGeneratedSource(String fileName) {
        var path = manifest.getFiles()
                .stream()
                .filter(file -> file.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow();
        return manifest.expectFileString(path);
    }

    private static String requiredStructure(String name, int memberCount) {
        var result = new StringBuilder()
                .append("$version: \"2\"\n")
                .append("namespace smithy.java.codegen.presence\n\n")
                .append("structure ")
                .append(name)
                .append(" {\n");
        for (int i = 0; i < memberCount; i++) {
            result.append("    @required\n")
                    .append("    member")
                    .append(i)
                    .append(": String\n");
        }
        return result.append("}\n").toString();
    }

}
