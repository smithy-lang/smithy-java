/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.language.Endpoint;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.Identifier;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.BooleanEquals;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.IsSet;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.Literal;
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter;
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType;
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameters;
import software.amazon.smithy.rulesengine.language.syntax.rule.Condition;
import software.amazon.smithy.rulesengine.language.syntax.rule.EndpointRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.ErrorRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.TreeRule;

public class DecisionTreeEndpointResolverTest {

    private Context context;
    private Map<String, Function<Context, Object>> builtinProviders;
    private TestApiOperation operation;
    private TestSerializableStruct input;

    @BeforeEach
    void setUp() {
        context = Context.create();
        builtinProviders = new HashMap<>();
        operation = new TestApiOperation();
        input = new TestSerializableStruct();
    }

    @Test
    void testSimpleEndpointResolution() throws Exception {
        var url = "https://example.com";
        var endpointRule = EndpointRule.builder().endpoint(Endpoint.builder().url(Expression.of(url)).build());

        EndpointRuleSet ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder().build())
                .rules(List.of(endpointRule))
                .build();

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);
        var endpoint = future.get();

        assertNotNull(endpoint);
        assertEquals(URI.create(url), endpoint.uri());
    }

    @Test
    void testParameterWithDefault() throws Exception {
        Parameter param = Parameter.builder()
                .name(Identifier.of("region"))
                .type(ParameterType.STRING)
                .required(true)
                .defaultValue(Value.stringValue("us-east-1"))
                .build();

        EndpointRule endpointRule = EndpointRule.builder()
                .endpoint(Endpoint.builder().url(Expression.getReference(Identifier.of("region"))).build());

        EndpointRuleSet ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder().addParameter(param).build())
                .rules(List.of(endpointRule))
                .build();

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);
        var endpoint = future.get();

        assertEquals(URI.create("us-east-1"), endpoint.uri());
    }

    @Test
    void testParameterOverridesDefault() throws Exception {
        Parameter param = Parameter.builder()
                .name(Identifier.of("region"))
                .type(ParameterType.STRING)
                .required(true)
                .defaultValue(Value.stringValue("us-east-1"))
                .build();

        var endpointRule = EndpointRule.builder()
                .endpoint(Endpoint.builder().url(Expression.getReference(Identifier.of("region"))).build());

        EndpointRuleSet ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder().addParameter(param).build())
                .rules(List.of(endpointRule))
                .build();

        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("region", "us-west-2");
        context = context.put(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS, additionalParams);

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);
        var endpoint = future.get();

        assertEquals(URI.create("us-west-2"), endpoint.uri());
    }

    @Test
    void testBuiltinParameter() throws Exception {
        Parameter param = Parameter.builder()
                .name(Identifier.of("endpoint"))
                .type(ParameterType.STRING)
                .builtIn("SDK::Endpoint")
                .build();

        // Need to check isSet before using optional parameter
        Condition isSetCondition = Condition.builder()
                .fn(IsSet.ofExpressions(Expression.getReference(Identifier.of("endpoint"))))
                .build();

        EndpointRule endpointRule = EndpointRule.builder()
                .conditions(List.of(isSetCondition))
                .endpoint(Endpoint.builder().url(Expression.getReference(Identifier.of("endpoint"))).build());

        EndpointRuleSet ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder().addParameter(param).build())
                .rules(List.of(endpointRule))
                .build();

        builtinProviders.put("SDK::Endpoint", ctx -> "https://custom.example.com");

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);
        var endpoint = future.get();

        assertEquals(URI.create("https://custom.example.com"), endpoint.uri());
    }

    @Test
    void testBuiltinReturnsNull() throws Exception {
        Parameter param = Parameter.builder()
                .name(Identifier.of("endpoint"))
                .type(ParameterType.STRING)
                .builtIn("SDK::Endpoint")
                .required(true)
                .defaultValue(Value.stringValue("https://default.com"))
                .build();

        EndpointRule endpointRule = EndpointRule.builder()
                .endpoint(Endpoint.builder().url(Expression.getReference(Identifier.of("endpoint"))).build());

        EndpointRuleSet ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder().addParameter(param).build())
                .rules(List.of(endpointRule))
                .build();

        builtinProviders.put("SDK::Endpoint", ctx -> null);

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);
        var endpoint = future.get();

        assertEquals(URI.create("https://default.com"), endpoint.uri());
    }

    @Test
    void testMultipleParameterTypes() throws Exception {
        var stringParam = Parameter.builder()
                .name(Identifier.of("bucket"))
                .type(ParameterType.STRING)
                .build();

        var boolParam = Parameter.builder()
                .name(Identifier.of("dualstack"))
                .type(ParameterType.BOOLEAN)
                .required(true)
                .defaultValue(Value.booleanValue(false))
                .build();

        var intParam = Parameter.builder()
                .name(Identifier.of("port"))
                .type(ParameterType.STRING)
                .required(true)
                .defaultValue(Value.stringValue("443"))
                .build();

        var endpointRule = EndpointRule.builder()
                .endpoint(Endpoint.builder().url(Expression.of("https://example.com")).build());

        var ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder()
                        .addParameter(stringParam)
                        .addParameter(boolParam)
                        .addParameter(intParam)
                        .build())
                .rules(List.of(endpointRule))
                .build();

        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("bucket", "my-bucket");
        additionalParams.put("dualstack", true);
        context = context.put(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS, additionalParams);

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);
        var endpoint = future.get();

        assertNotNull(endpoint);
        assertEquals(URI.create("https://example.com"), endpoint.uri());
    }

    @Test
    void testErrorRuleEvaluation() {
        ErrorRule errorRule = ErrorRule.builder().error(Expression.of("Invalid configuration"));

        // Add a fallback endpoint rule that won't be reached
        EndpointRule fallbackRule = EndpointRule.builder()
                .endpoint(Endpoint.builder().url(Expression.of("https://fallback.com")).build());

        // Use TreeRule to test error within a condition
        Condition alwaysTrue = Condition.builder()
                .fn(BooleanEquals.ofExpressions(Literal.booleanLiteral(true), Literal.booleanLiteral(true)))
                .build();

        var treeRule = TreeRule.builder().conditions(List.of(alwaysTrue)).treeRule(List.of(errorRule));

        EndpointRuleSet ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder().build())
                .rules(List.of(treeRule, fallbackRule))
                .build();

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);

        Exception exception = assertThrows(Exception.class, future::get);
        assertInstanceOf(RulesEvaluationError.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Invalid configuration"));
    }

    @Test
    void testParameterPriority() throws Exception {
        Parameter param = Parameter.builder()
                .name(Identifier.of("region"))
                .type(ParameterType.STRING)
                .required(true)
                .defaultValue(Value.stringValue("default-region"))
                .builtIn("AWS::Region")
                .build();

        EndpointRule endpointRule = EndpointRule.builder()
                .endpoint(Endpoint.builder().url(Expression.getReference(Identifier.of("region"))).build());

        EndpointRuleSet ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder().addParameter(param).build())
                .rules(List.of(endpointRule))
                .build();

        builtinProviders.put("AWS::Region", ctx -> "builtin-region");

        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("region", "supplied-region");
        context = context.put(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS, additionalParams);

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);
        var endpoint = future.get();

        assertEquals(URI.create("supplied-region"), endpoint.uri());
    }

    @Test
    void testListParameterConversion() throws Exception {
        Parameter param = Parameter.builder()
                .name(Identifier.of("regions"))
                .type(ParameterType.STRING_ARRAY)
                .build();

        EndpointRule endpointRule = EndpointRule.builder()
                .endpoint(Endpoint.builder().url(Expression.of(("https://multi-region.example.com"))).build());

        EndpointRuleSet ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder().addParameter(param).build())
                .rules(List.of(endpointRule))
                .build();

        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("regions", List.of("us-east-1", "us-west-2"));
        context = context.put(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS, additionalParams);

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);
        var endpoint = future.get();

        assertNotNull(endpoint);
    }

    @Test
    void testMapParameterConversion() throws Exception {
        var endpointRule = EndpointRule.builder()
                .endpoint(Endpoint.builder().url(Expression.of(("https://example.com"))).build());

        var ruleSet = EndpointRuleSet.builder()
                .parameters(Parameters.builder().build())
                .rules(List.of(endpointRule))
                .build();

        Map<String, Object> additionalParams = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put("timeout", 30);
        config.put("retries", 3);
        additionalParams.put("config", config);
        context = context.put(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS, additionalParams);

        var resolver = new DecisionTreeEndpointResolver(ruleSet, List.of(), builtinProviders);
        var params = createParams(operation, input, context);
        var future = resolver.resolveEndpoint(params);
        var endpoint = future.get();

        assertNotNull(endpoint);
    }

    private static EndpointResolverParams createParams(
            ApiOperation<?, ?> operation,
            SerializableStruct input,
            Context context
    ) {
        return EndpointResolverParams.builder().context(context).inputValue(input).operation(operation).build();
    }

    private static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("test#TestOperation")).build();
    private static final Schema INPUT = Schema.structureBuilder(ShapeId.from("test#TestInput")).build();

    private static class TestApiOperation implements ApiOperation<TestSerializableStruct, TestSerializableStruct> {
        @Override
        public ShapeBuilder<TestSerializableStruct> inputBuilder() {
            return null;
        }

        @Override
        public ShapeBuilder<TestSerializableStruct> outputBuilder() {
            return null;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public Schema inputSchema() {
            return INPUT;
        }

        @Override
        public Schema outputSchema() {
            return INPUT;
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
    }

    private static class TestSerializableStruct implements SerializableStruct {
        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}
    }
}
