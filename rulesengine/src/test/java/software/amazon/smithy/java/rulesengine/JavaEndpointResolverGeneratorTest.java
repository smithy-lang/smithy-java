/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointContext;
import software.amazon.smithy.java.endpoints.EndpointResolverParams;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.Identifier;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Template;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Coalesce;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.StringEquals;
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.Literal;
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter;
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType;
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameters;
import software.amazon.smithy.rulesengine.language.syntax.rule.Condition;
import software.amazon.smithy.rulesengine.language.syntax.rule.EndpointRule;
import software.amazon.smithy.rulesengine.language.syntax.rule.NoMatchRule;
import software.amazon.smithy.rulesengine.logic.bdd.Bdd;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;

class JavaEndpointResolverGeneratorTest {
    private static final String PACKAGE = "software.amazon.smithy.java.rulesengine.generated";
    private static final String CLASS_NAME = "GeneratedTestResolver";
    private static final Schema INPUT_SCHEMA =
            Schema.structureBuilder(ShapeId.from("smithy.example#Input")).build();

    @TempDir
    Path tempDir;

    @Test
    void generatesFieldBasedResolverEquivalentToVm() throws Exception {
        RulesEngineBuilder engine = new RulesEngineBuilder();
        Bytecode bytecode = engine.compile(testBdd());
        String source = new JavaEndpointResolverGenerator(bytecode).generate(PACKAGE, CLASS_NAME);

        assertFalse(source.contains("Object[]"));
        assertFalse(source.contains("VarHandle"));
        assertTrue(source.contains("private software.amazon.smithy.java.endpoints.Endpoint nodeP2"));
        assertTrue(source.contains("while (true)"));

        GeneratedEndpointResolver<?> generated = compile(source);
        var vm = new BytecodeEndpointResolver(
                bytecode,
                engine.getExtensions(),
                engine.getBuiltinProviders());
        var parameters = generated.createParameters(Map.of("Required", "present"));

        Context fallbackContext = Context.create();
        assertEquivalent(vm, generated, parameters, fallbackContext, "https://default.fallback.example.com");

        Context customContext = Context.create().put(
                EndpointContext.CUSTOM_ENDPOINT,
                Endpoint.builder().uri("https://custom.example.com").build());
        assertEquivalent(vm, generated, parameters, customContext, "https://custom-selected.example.com");

        assertEquivalent(vm, generated, parameters, fallbackContext, "https://default.fallback.example.com");
        var error = assertThrows(
                RulesEvaluationError.class,
                () -> generated.resolveEndpoint(Context.create(), generated.createParameters()));
        assertTrue(error.getMessage().contains("Required"));
    }

    @Test
    void generatedParametersFallBackToThreadLocalStateWhenShared() throws Exception {
        RulesEngineBuilder engine = new RulesEngineBuilder();
        GeneratedEndpointResolver<?> generated = compile(
                new JavaEndpointResolverGenerator(engine.compile(testBdd())).generate(PACKAGE, CLASS_NAME));
        var parameters = generated.createParameters(Map.of("Required", "present"));
        int threadCount = 8;
        var start = new CountDownLatch(1);
        var failures = new ArrayList<Throwable>();

        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int call = 0; call < 100; call++) {
                        String uri = generated.resolveEndpoint(Context.create(), parameters).uri().toString();
                        if (!"https://default.fallback.example.com".equals(uri)) {
                            throw new AssertionError(uri);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    failures.add(e);
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void materializesExtraPropertiesForLegacyExtensions() {
        var extension = new CapturingExtension();
        var state = new TestState(new RulesExtension[] {extension});
        state.setContext(Context.create());
        Map<String, Object> properties = state.authSchemeProperties(
                "S3Express",
                "backend",
                false,
                "disableDoubleEncoding",
                "sigv4-s3express",
                "name",
                "s3express",
                "signingName",
                "us-east-1",
                "signingRegion");

        Endpoint endpoint = state.endpoint("https://example.com", properties, Map.of());

        assertEquals("S3Express", extension.properties.get("backend"));
        assertEquals("sigv4-s3express",
                EndpointUtils.getProperty(
                        ((List<?>) extension.properties.get("authSchemes")).getFirst(),
                        "name"));
        assertEquals("https://example.com", endpoint.uri().toString());

        properties = state.authSchemeProperties(
                null,
                "backend",
                false,
                "disableDoubleEncoding",
                "sigv4-s3express",
                "name",
                "s3express",
                "signingName",
                "us-east-1",
                "signingRegion");
        state.endpoint("https://example.com", properties, Map.of());
        assertTrue(extension.properties.containsKey("backend"));
        assertNull(extension.properties.get("backend"));
    }

    private static EndpointBddTrait testBdd() {
        var endpoint = Parameter.builder()
                .name("Endpoint")
                .type(ParameterType.STRING)
                .builtIn("SDK::Endpoint")
                .build();
        var suffix = Parameter.builder()
                .name("Suffix")
                .type(ParameterType.STRING)
                .required(true)
                .defaultValue(Value.stringValue("default"))
                .build();
        var required = Parameter.builder()
                .name("Required")
                .type(ParameterType.STRING)
                .required(true)
                .build();
        var parameters = Parameters.builder()
                .addParameter(endpoint)
                .addParameter(suffix)
                .addParameter(required)
                .build();
        Expression fallback = Literal.stringLiteral(Template.fromString("https://fallback.example.com"));
        var condition = Condition.builder()
                .fn(StringEquals.ofExpressions(
                        Coalesce.ofExpressions(
                                Expression.getReference(Identifier.of("Endpoint")),
                                fallback),
                        fallback))
                .build();
        var fallbackRule = EndpointRule.builder()
                .endpoint(software.amazon.smithy.rulesengine.language.Endpoint.builder()
                        .url(Literal.stringLiteral(
                                Template.fromString("https://{Suffix}.fallback.example.com")))
                        .build());
        var customRule = EndpointRule.builder()
                .endpoint(software.amazon.smithy.rulesengine.language.Endpoint.builder()
                        .url(Literal.stringLiteral(Template.fromString("https://custom-selected.example.com")))
                        .build());
        return EndpointBddTrait.builder()
                .parameters(parameters)
                .conditions(List.of(condition))
                .results(List.of(NoMatchRule.INSTANCE, fallbackRule, customRule))
                .bdd(new Bdd(2, 1, 3, 2, consumer -> {
                    consumer.accept(-1, 1, -1);
                    consumer.accept(0, 100_000_001, 100_000_002);
                }))
                .build();
    }

    private GeneratedEndpointResolver<?> compile(String source) throws Exception {
        Path sourceDir = tempDir.resolve(PACKAGE.replace('.', '/'));
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve(CLASS_NAME + ".java");
        Files.writeString(sourceFile, source);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-proc:none",
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                tempDir.toString(),
                sourceFile.toString());
        assertEquals(0, result);
        var loader = new URLClassLoader(
                new java.net.URL[] {tempDir.toUri().toURL()},
                getClass().getClassLoader());
        return (GeneratedEndpointResolver<?>) Class.forName(PACKAGE + "." + CLASS_NAME, true, loader)
                .getConstructor()
                .newInstance();
    }

    private static void assertEquivalent(
            BytecodeEndpointResolver vm,
            GeneratedEndpointResolver<?> generated,
            GeneratedEndpointResolver.GeneratedParameters parameters,
            Context context,
            String expected
    ) {
        Context vmContext = context.put(
                RulesEngineSettings.ADDITIONAL_ENDPOINT_PARAMS,
                Map.of("Required", "present"));
        Endpoint vmEndpoint = vm.resolveEndpoint(EndpointResolverParams.builder()
                .operation(new TestOperation())
                .inputValue(new TestInput())
                .context(vmContext)
                .build());
        Endpoint generatedEndpoint = generated.resolveEndpoint(context, parameters);
        assertEquals(expected, vmEndpoint.uri().toString());
        assertEquals(vmEndpoint, generatedEndpoint);
        assertNotSame(vmEndpoint, generatedEndpoint);
    }

    private static final class TestState extends GeneratedEndpointResolver.EvaluationState {
        TestState(RulesExtension[] extensions) {
            super(extensions);
        }

        @Override
        public void put(String name, Object value) {}
    }

    private static final class CapturingExtension implements RulesExtension {
        private Map<String, Object> properties;

        @Override
        public void extractEndpointProperties(
                Endpoint.Builder builder,
                Context context,
                Map<String, Object> properties,
                Map<String, List<String>> headers
        ) {
            this.properties = properties;
        }
    }

    private static final class TestOperation implements ApiOperation<TestInput, TestInput> {
        @Override
        public ShapeBuilder<TestInput> inputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<TestInput> outputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema schema() {
            return Schema.createOperation(ShapeId.from("smithy.example#Operation"));
        }

        @Override
        public Schema inputSchema() {
            return INPUT_SCHEMA;
        }

        @Override
        public Schema outputSchema() {
            return INPUT_SCHEMA;
        }

        @Override
        public TypeRegistry errorRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ShapeId> effectiveAuthSchemes() {
            return List.of();
        }

        @Override
        public List<Schema> errorSchemas() {
            return List.of();
        }

        @Override
        public ApiService service() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestInput implements SerializableStruct {
        @Override
        public Schema schema() {
            return INPUT_SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }
}
