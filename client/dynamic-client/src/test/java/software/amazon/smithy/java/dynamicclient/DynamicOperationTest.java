/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.traits.HttpBasicAuthTrait;
import software.amazon.smithy.model.traits.HttpBearerAuthTrait;
import software.amazon.smithy.model.traits.synthetic.NoAuthTrait;

public class DynamicOperationTest {
    @Test
    public void supportsDataStreamOperations() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"

                        namespace smithy.example

                        service S {
                            operations: [PutFoo]
                        }

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
        var service = model.expectShape(ShapeId.from("smithy.example#S")).asServiceShape().get();
        var operation = model.expectShape(ShapeId.from("smithy.example#PutFoo")).asOperationShape().get();

        var op = DynamicOperation.create(
                operation,
                converter,
                model,
                service,
                TypeRegistry.empty(),
                (id, b) -> {});

        // Data streams don't need event builder suppliers
        assertThat(op.inputEventBuilderSupplier(), is(nullValue()));
        assertThat(op.outputEventBuilderSupplier(), is(nullValue()));
        assertThat(op.inputStreamMember(), is(op.inputSchema().member("someStream")));
    }

    @Test
    public void createsInputEventStreamingOperation() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"

                        namespace smithy.example

                        service S {
                            operations: [PutFoo]
                        }

                        operation PutFoo {
                            input := {
                                stream: Events
                            }
                            output := {}
                        }

                        @streaming
                        union Events {
                            data: Data
                        }

                        structure Data {
                            value: String
                        }
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var service = model.expectShape(ShapeId.from("smithy.example#S")).asServiceShape().get();
        var operation = model.expectShape(ShapeId.from("smithy.example#PutFoo")).asOperationShape().get();

        var op = DynamicOperation.create(
                operation,
                converter,
                model,
                service,
                TypeRegistry.empty(),
                (id, b) -> {});

        assertThat(op.inputEventBuilderSupplier(), is(notNullValue()));
        assertThat(op.outputEventBuilderSupplier(), is(nullValue()));
        assertThat(op.inputEventBuilderSupplier().get().schema().type(),
                equalTo(ShapeType.UNION));
    }

    @Test
    public void createsOutputEventStreamingOperation() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"

                        namespace smithy.example

                        service S {
                            operations: [GetFoo]
                        }

                        operation GetFoo {
                            input := {}
                            output := {
                                stream: Events
                            }
                        }

                        @streaming
                        union Events {
                            data: Data
                        }

                        structure Data {
                            value: String
                        }
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var service = model.expectShape(ShapeId.from("smithy.example#S")).asServiceShape().get();
        var operation = model.expectShape(ShapeId.from("smithy.example#GetFoo")).asOperationShape().get();

        var op = DynamicOperation.create(
                operation,
                converter,
                model,
                service,
                TypeRegistry.empty(),
                (id, b) -> {});

        assertThat(op.inputEventBuilderSupplier(), is(nullValue()));
        assertThat(op.outputEventBuilderSupplier(), is(notNullValue()));
        assertThat(op.outputEventBuilderSupplier().get().schema().type(),
                equalTo(ShapeType.UNION));
    }

    @Test
    public void createsBidirectionalEventStreamingOperation() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"

                        namespace smithy.example

                        service S {
                            operations: [Chat]
                        }

                        operation Chat {
                            input := {
                                inputStream: InputEvents
                            }
                            output := {
                                outputStream: OutputEvents
                            }
                        }

                        @streaming
                        union InputEvents {
                            message: Message
                        }

                        @streaming
                        union OutputEvents {
                            reply: Reply
                        }

                        structure Message {
                            text: String
                        }

                        structure Reply {
                            text: String
                        }
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var service = model.expectShape(ShapeId.from("smithy.example#S")).asServiceShape().get();
        var operation = model.expectShape(ShapeId.from("smithy.example#Chat")).asOperationShape().get();

        var op = DynamicOperation.create(
                operation,
                converter,
                model,
                service,
                TypeRegistry.empty(),
                (id, b) -> {});

        assertThat(op.inputEventBuilderSupplier(), is(notNullValue()));
        assertThat(op.outputEventBuilderSupplier(), is(notNullValue()));
    }

    @Test
    public void resolvesEffectiveAuthSchemesPerOperation() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"

                        namespace smithy.example

                        @httpBasicAuth
                        @httpBearerAuth
                        @auth([httpBasicAuth])
                        service S {
                            operations: [InheritedAuth, OverriddenAuth, NoAuth, OptionalAuth]
                        }

                        operation InheritedAuth {
                            input := {}
                            output := {}
                        }

                        @auth([httpBearerAuth])
                        operation OverriddenAuth {
                            input := {}
                            output := {}
                        }

                        @auth([])
                        operation NoAuth {
                            input := {}
                            output := {}
                        }

                        @optionalAuth
                        operation OptionalAuth {
                            input := {}
                            output := {}
                        }
                        """)
                .assemble()
                .unwrap();

        assertThat(
                createOperation(model, "InheritedAuth").effectiveAuthSchemes(),
                equalTo(List.of(HttpBasicAuthTrait.ID)));
        assertThat(
                createOperation(model, "OverriddenAuth").effectiveAuthSchemes(),
                equalTo(List.of(HttpBearerAuthTrait.ID)));
        assertThat(
                createOperation(model, "NoAuth").effectiveAuthSchemes(),
                equalTo(List.of(NoAuthTrait.ID)));
        assertThat(
                createOperation(model, "OptionalAuth").effectiveAuthSchemes(),
                equalTo(List.of(HttpBasicAuthTrait.ID, NoAuthTrait.ID)));
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

        var serviceSchema = Schema.createService(ShapeId.from("smithy.example#S"));
        ApiService apiService = () -> serviceSchema;

        var o = new DynamicOperation(
                apiService,
                operationSchema,
                input,
                output,
                Set.of(),
                registry,
                List.of(),
                null,
                null);

        assertThat(o.schema().id(), equalTo(ShapeId.from("smithy.example#PutFoo")));
        assertThat(o.schema().hasTrait(TraitKey.get(DeprecatedTrait.class)), is(true));
        assertThat(o.inputSchema().id(), equalTo(ShapeId.from("smithy.example#PutFooInput")));
        assertThat(o.outputSchema().id(), equalTo(ShapeId.from("smithy.example#PutFooOutput")));
        assertThat(o.inputBuilder().schema().id(), equalTo(ShapeId.from("smithy.example#PutFooInput")));
        assertThat(o.outputBuilder().schema().id(), equalTo(ShapeId.from("smithy.example#PutFooOutput")));
        assertThat(o.errorRegistry(), is(registry));
        assertThat(o.effectiveAuthSchemes(), empty());
    }

    private static DynamicOperation createOperation(Model model, String operationName) {
        var converter = new SchemaConverter(model);
        var service = model.expectShape(ShapeId.from("smithy.example#S")).asServiceShape().get();
        var operation = model.expectShape(ShapeId.from("smithy.example#" + operationName)).asOperationShape().get();
        return DynamicOperation.create(
                operation,
                converter,
                model,
                service,
                TypeRegistry.empty(),
                (id, b) -> {});
    }
}
