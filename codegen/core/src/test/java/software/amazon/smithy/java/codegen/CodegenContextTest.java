/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.CorsTrait;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.EventHeaderTrait;
import software.amazon.smithy.model.traits.EventPayloadTrait;
import software.amazon.smithy.model.traits.HostLabelTrait;
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
        var context = new CodeGenerationContext(
            model,
            new JavaCodegenSettings(
                SERVICE_ID,
                "ns.foo",
                null,
                "",
                "",
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList()
            ),
            new JavaSymbolProvider(model, model.expectShape(SERVICE_ID).asServiceShape().get(), "ns.foo"),
            new MockManifest(),
            List.of()
        );

        assertThat(
            context.runtimeTraits(),
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
                StreamingTrait.ID
            )
        );
    }

    @Test
    void getsCorrectTraitsWithNoProtocolOrAuth() {
        var context = new CodeGenerationContext(
            model,
            new JavaCodegenSettings(
                NO_PROTOCOL_SERVICE_ID,
                "ns.foo",
                null,
                "",
                "",
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList()
            ),
            new JavaSymbolProvider(model, model.expectShape(NO_PROTOCOL_SERVICE_ID).asServiceShape().get(), "ns.foo"),
            new MockManifest(),
            List.of()
        );

        assertThat(
            context.runtimeTraits(),
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
                StreamingTrait.ID
            )
        );
    }
}
