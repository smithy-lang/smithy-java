/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.HashSet;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.java.codegen.utils.TestJavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;

public class CodegenContextTest {
    private static Model model;
    private static final ShapeId SERVICE_ID = ShapeId.from("smithy.java.codegen#TestService");
    private static final ShapeId NO_PROTOCOL_SERVICE_ID = ShapeId.from("smithy.java.codegen#NoProtocolService");

    @BeforeAll
    public static void before() {
        model = Model.assembler()
                .addImport(Objects.requireNonNull(CodegenContextTest.class.getResource("codegen-context-test.smithy")))
                .assemble()
                .unwrap();
    }

    @Test
    void getsCorrectRuntimeTraitsForProtocolsAndAuth() {
        TestJavaCodegenPlugin plugin = new TestJavaCodegenPlugin();
        PluginContext context = PluginContext.builder()
                .fileManifest(new MockManifest())
                .settings(
                        ObjectNode.builder()
                                .withMember("service", "smithy.java.codegen#TestService")
                                .withMember("namespace", "software.amazon.smithy.java.codegen.test")
                                .withMember("runtimeTraitsSelector", "[id=smithy.java.codegen#selectedTrait]")
                                .withMember("runtimeTraits",
                                        ArrayNode.builder().withValue("smithy.api#httpError").build())
                                .build())
                .model(model)
                .build();
        plugin.execute(context);

        var expected = new HashSet<>(CodeGenerationContext.PRELUDE_RUNTIME_TRAITS);
        expected.add(ShapeId.from("smithy.java.codegen#selectedTrait"));
        expected.add(ShapeId.from("smithy.api#httpError"));
        expected.add(ShapeId.from("smithy.api#httpPayload"));
        expected.add(ShapeId.from("smithy.api#timestampFormat"));
        expected.add(ShapeId.from("smithy.api#cors"));
        expected.add(ShapeId.from("smithy.api#http"));
        expected.add(ShapeId.from("smithy.api#httpQuery"));
        expected.add(ShapeId.from("smithy.api#endpoint"));

        assertThat(
                plugin.capturedContext.runtimeTraits(),
                containsInAnyOrder(expected.toArray()));
    }

    @Test
    void getsCorrectTraitsWithNoProtocolOrAuth() {
        TestJavaCodegenPlugin plugin = new TestJavaCodegenPlugin();
        PluginContext context = PluginContext.builder()
                .fileManifest(new MockManifest())
                .settings(
                        ObjectNode.builder()
                                .withMember("service", "smithy.java.codegen#NoProtocolService")
                                .withMember("namespace", "software.amazon.smithy.java.codegen.test")
                                .build())
                .model(model)
                .build();
        plugin.execute(context);

        assertThat(
                plugin.capturedContext.runtimeTraits(),
                containsInAnyOrder(CodeGenerationContext.PRELUDE_RUNTIME_TRAITS.toArray()));
    }

    public static class SelectedTrait extends AnnotationTrait {
        public static final ShapeId ID = ShapeId.from("smithy.java.codegen#selectedTrait");

        public SelectedTrait() {
            this(Node.objectNode());
        }

        public SelectedTrait(ObjectNode node) {
            super(ID, node);
        }

        public static final class Provider extends AnnotationTrait.Provider<SelectedTrait> {
            public Provider() {
                super(SelectedTrait.ID, SelectedTrait::new);
            }
        }
    }
}
