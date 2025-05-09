/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.rulesengine.traits.StaticContextParamDefinition;
import software.amazon.smithy.rulesengine.traits.StaticContextParamsTrait;

public class EndpointRulesResolverTest {
    @Test
    public void resolvesEndpointWithoutStaticParams() {
        EndpointResolverParams params = EndpointResolverParams.builder()
                .inputValue(getInput())
                .operation(getOperation(Map.of()))
                .build();

        var program = new RulesEngine().precompiledBuilder()
                .bytecode(
                        RulesProgram.VERSION,
                        (byte) 0,
                        (byte) 0,
                        RulesProgram.LOAD_CONST,
                        (byte) 0,
                        RulesProgram.RETURN_ENDPOINT,
                        (byte) 0)
                .constantPool("https://example.com")
                .build();
        var resolver = new EndpointRulesResolver(program);
        var result = resolver.resolveEndpoint(params).join();

        assertThat(result.uri().toString(), equalTo("https://example.com"));
    }

    private SerializableStruct getInput() {
        return new SerializableStruct() {
            @Override
            public Schema schema() {
                return null;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {}

            @Override
            public <T> T getMemberValue(Schema member) {
                return null;
            }
        };
    }

    private ApiOperation<SerializableStruct, SerializableStruct> getOperation(
            Map<String, StaticContextParamDefinition> staticParams
    ) {
        return new ApiOperation<>() {
            @Override
            public ShapeBuilder<SerializableStruct> inputBuilder() {
                return null;
            }

            @Override
            public ShapeBuilder<SerializableStruct> outputBuilder() {
                return null;
            }

            @Override
            public Schema schema() {
                Trait[] traits = null;
                if (staticParams != null) {
                    traits = new Trait[] {
                            StaticContextParamsTrait
                                    .builder()
                                    .parameters(staticParams)
                                    .build()
                    };
                } else {
                    traits = new Trait[0];
                }
                return Schema.createOperation(ShapeId.from("smithy.example#Foo"), traits);
            }

            @Override
            public Schema inputSchema() {
                return null;
            }

            @Override
            public Schema outputSchema() {
                return null;
            }

            @Override
            public TypeRegistry errorRegistry() {
                return null;
            }

            @Override
            public List<ShapeId> effectiveAuthSchemes() {
                return List.of();
            }

            @Override
            public ApiService service() {
                return null;
            }
        };
    }

    @Test
    public void resolvesEndpointWithStaticParams() {
        var op = getOperation(Map.of("foo",
                StaticContextParamDefinition.builder()
                        .value(Node.from("https://foo.com"))
                        .build()));
        EndpointResolverParams params = EndpointResolverParams.builder()
                .inputValue(getInput())
                .operation(op)
                .build();

        var program = new RulesEngine().precompiledBuilder()
                .bytecode(
                        RulesProgram.VERSION,
                        (byte) 1,
                        (byte) 0,
                        RulesProgram.LOAD_REGISTER, // load foo
                        (byte) 0,
                        RulesProgram.RETURN_ENDPOINT,
                        (byte) 0)
                .constantPool("https://example.com")
                .parameters(new ParamDefinition("foo", false, null, null))
                .build();
        var resolver = new EndpointRulesResolver(program);
        var result = resolver.resolveEndpoint(params).join();

        assertThat(result.uri().toString(), equalTo("https://foo.com"));
    }

    @Test
    public void returnsCfInsteadOfThrowingOnError() {
        EndpointResolverParams params = EndpointResolverParams.builder()
                .inputValue(getInput())
                .operation(getOperation(Map.of()))
                .build();

        var program = new RulesEngine().precompiledBuilder()
                .bytecode(
                        RulesProgram.VERSION,
                        (byte) 1,
                        (byte) 0)
                .constantPool("https://example.com")
                .parameters(new ParamDefinition("foo", false, null, null))
                .build();
        var resolver = new EndpointRulesResolver(program);
        var result = resolver.resolveEndpoint(params);

        try {
            result.join();
            Assertions.fail("Expected to throw");
        } catch (CompletionException e) {
            assertThat(e.getCause(), instanceOf(RulesEvaluationError.class));
        }
    }
}
