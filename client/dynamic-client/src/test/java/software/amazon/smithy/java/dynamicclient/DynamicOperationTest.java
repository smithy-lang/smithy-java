/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DeprecatedTrait;

public class DynamicOperationTest {
    @Test
    public void doesNotCurrentlySupportStreaming() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"

                        namespace smithy.example

                        operation PutFoo {
                            input := {
                                @required
                                someStream: SomeStream
                            }
                            output := {}
                        }

                        @streaming
                        blob SomeStream
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var registry = TypeRegistry.empty();
        var operationSchema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#PutFoo")));
        var input = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#PutFooInput")));
        var output = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#PutFooOutput")));

        var e = Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            DynamicOperation o = new DynamicOperation(
                    ShapeId.from("smithy.example#S"),
                    operationSchema,
                    input,
                    output,
                    Set.of(),
                    registry,
                    List.of());
        });

        assertThat(e.getMessage(), containsString("does not support streaming"));
    }

    @Test
    public void convertsSchemas() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"

                        namespace smithy.example

                        @deprecated
                        operation PutFoo {
                            input := {}
                            output := {}
                        }
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var registry = TypeRegistry.empty();
        var operationSchema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#PutFoo")));
        var input = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#PutFooInput")));
        var output = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#PutFooOutput")));

        var o = new DynamicOperation(
                ShapeId.from("smithy.example#S"),
                operationSchema,
                input,
                output,
                Set.of(),
                registry,
                List.of());

        assertThat(o.schema().id(), equalTo(ShapeId.from("smithy.example#PutFoo")));
        assertThat(o.schema().hasTrait(TraitKey.get(DeprecatedTrait.class)), is(true));
        assertThat(o.inputSchema().id(), equalTo(ShapeId.from("smithy.example#PutFooInput")));
        assertThat(o.outputSchema().id(), equalTo(ShapeId.from("smithy.example#PutFooOutput")));
        assertThat(o.inputBuilder().schema().id(), equalTo(ShapeId.from("smithy.example#PutFooInput")));
        assertThat(o.outputBuilder().schema().id(), equalTo(ShapeId.from("smithy.example#PutFooOutput")));
        assertThat(o.errorRegistry(), is(registry));
        assertThat(o.effectiveAuthSchemes(), empty());
    }
}
