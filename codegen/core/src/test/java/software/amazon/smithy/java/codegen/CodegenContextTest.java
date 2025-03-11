/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

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
import software.amazon.smithy.model.traits.CorsTrait;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.EventHeaderTrait;
import software.amazon.smithy.model.traits.EventPayloadTrait;
import software.amazon.smithy.model.traits.HostLabelTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.PaginatedTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequestCompressionTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.RequiresLengthTrait;
import software.amazon.smithy.model.traits.RetryableTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;
import software.amazon.smithy.model.traits.XmlAttributeTrait;
import software.amazon.smithy.model.traits.XmlFlattenedTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

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

        assertThat(
                plugin.capturedContext.runtimeTraits(),
                containsInAnyOrder(
                        // Prelude validation traits
                        LengthTrait.ID,
                        PatternTrait.ID,
                        RangeTrait.ID,
                        RequiredTrait.ID,
                        SensitiveTrait.ID,
                        SparseTrait.ID,
                        UniqueItemsTrait.ID,
                        RequiresLengthTrait.ID,
                        ErrorTrait.ID,
                        DefaultTrait.ID,
                        // Base Prelude Protocol traits
                        JsonNameTrait.ID,
                        TimestampFormatTrait.ID,
                        MediaTypeTrait.ID,
                        XmlNameTrait.ID,
                        XmlFlattenedTrait.ID,
                        XmlAttributeTrait.ID,
                        XmlNamespaceTrait.ID,
                        EventHeaderTrait.ID,
                        EventPayloadTrait.ID,
                        HostLabelTrait.ID,
                        EndpointTrait.ID,
                        // Protocol Traits
                        CorsTrait.ID,
                        HttpTrait.ID,
                        // Auth traits
                        HttpQueryTrait.ID,
                        HttpPayloadTrait.ID,
                        // Prelude behavior traits
                        PaginatedTrait.ID,
                        IdempotencyTokenTrait.ID,
                        RetryableTrait.ID,
                        RequestCompressionTrait.ID,
                        StreamingTrait.ID,
                        // Added by settings
                        HttpErrorTrait.ID,
                        ShapeId.from("smithy.java.codegen#selectedTrait")));
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
                containsInAnyOrder(
                        // Prelude Validation Traits
                        LengthTrait.ID,
                        PatternTrait.ID,
                        RangeTrait.ID,
                        RequiredTrait.ID,
                        SensitiveTrait.ID,
                        SparseTrait.ID,
                        UniqueItemsTrait.ID,
                        RequiresLengthTrait.ID,
                        ErrorTrait.ID,
                        DefaultTrait.ID,
                        // Base Prelude Protocol traits
                        JsonNameTrait.ID,
                        TimestampFormatTrait.ID,
                        MediaTypeTrait.ID,
                        XmlNameTrait.ID,
                        XmlFlattenedTrait.ID,
                        XmlAttributeTrait.ID,
                        XmlNamespaceTrait.ID,
                        EventHeaderTrait.ID,
                        EventPayloadTrait.ID,
                        HostLabelTrait.ID,
                        EndpointTrait.ID,
                        // Prelude behavior traits
                        PaginatedTrait.ID,
                        IdempotencyTokenTrait.ID,
                        RetryableTrait.ID,
                        RequestCompressionTrait.ID,
                        StreamingTrait.ID));
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
