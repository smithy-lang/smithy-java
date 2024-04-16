/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.CorsTrait;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.HostLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

public class CodegenContextTest {
    private static Model model;
    private static final ShapeId SERVICE_ID = ShapeId.from("smithy.java.codegen#TestService");

    @BeforeAll
    public static void before() {
        model = Model.assembler()
            .addImport(Objects.requireNonNull(CodegenContextTest.class.getResource("codegen-context-test.smithy")))
            .assemble()
            .unwrap();
    }

    @Test
    void getsCorrectRuntimeTraits() {
        var context = new CodeGenerationContext(
            model,
            new JavaCodegenSettings(SERVICE_ID, "ns.foo"),
            new JavaSymbolProvider(model, model.expectShape(SERVICE_ID).asServiceShape().get(), "ns.foo"),
            new MockManifest(),
            List.of()
        );
        System.out.println(context.runtimeTraits());

        assertThat(
            context.runtimeTraits(),
            containsInAnyOrder(
                // Prelude validation traits
                LengthTrait.ID,
                PatternTrait.ID,
                RangeTrait.ID,
                RequiredTrait.ID,
                SensitiveTrait.ID,
                // Protocol Traits
                TimestampFormatTrait.ID,
                CorsTrait.ID,
                EndpointTrait.ID,
                HostLabelTrait.ID,
                HttpTrait.ID,
                // Auth traits
                HttpQueryTrait.ID,
                HttpPayloadTrait.ID
            )
        );
    }
}
