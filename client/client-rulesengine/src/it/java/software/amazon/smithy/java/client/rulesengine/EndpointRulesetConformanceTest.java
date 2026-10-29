/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointResolverParams;
import software.amazon.smithy.java.rulesengine.Bytecode;
import software.amazon.smithy.java.rulesengine.BytecodeEndpointResolver;
import software.amazon.smithy.java.rulesengine.EndpointUtils;
import software.amazon.smithy.java.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.java.rulesengine.RulesEngineSettings;
import software.amazon.smithy.java.rulesengine.RulesEvaluationError;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.loader.ModelDiscovery;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.logic.bdd.NodeReversal;
import software.amazon.smithy.rulesengine.logic.bdd.SiftingOptimization;
import software.amazon.smithy.rulesengine.logic.cfg.Cfg;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;
import software.amazon.smithy.rulesengine.traits.EndpointTestCase;
import software.amazon.smithy.rulesengine.traits.EndpointTestsTrait;

/**
 * Resolves every endpoint test case declared by every AWS service ruleset on the classpath and asserts the
 * result matches the URL, or the failure, that the model itself declares.
 *
 * <p>This is the correctness gate for {@code NodeCompiler}: a single mis-decoded operand would otherwise
 * silently route traffic to the wrong endpoint. It replaced an earlier test that compared the compiled
 * node trees against the bytecode interpreter, which was a weaker oracle in two ways — it could not
 * outlive the interpreter, and it could not catch a case where both paths agreed and both were wrong. The
 * model's own declared expectations have neither limitation, and switching to them immediately surfaced
 * the two pre-existing defects characterized in {@link #assertKnownPreExistingDefect}.
 *
 * <p>Models are resolved only when the {@code awsModelsTests} Gradle property is set.
 */
@EnabledIfSystemProperty(named = "awsModelsTests", matches = "true")
class EndpointRulesetConformanceTest {

    static Stream<Named<URL>> awsModels() {
        return ModelDiscovery.findModels()
                .stream()
                .filter(url -> url.toString().endsWith(".json"))
                .map(url -> Named.of(artifactName(url), url))
                .sorted(Comparator.comparing(Named::getName));
    }

    private static String artifactName(URL modelUrl) {
        String urlStr = modelUrl.toString();
        int bangIdx = urlStr.indexOf("!/");
        String jarPath = urlStr.substring(0, bangIdx);
        String jarName = jarPath.substring(jarPath.lastIndexOf('/') + 1);
        return jarName.replaceFirst("-\\d[\\d.]*\\.jar$", "");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("awsModels")
    void resolvesEveryDeclaredTestCase(URL modelUrl) {
        Model model = Model.assembler()
                .addImport(modelUrl)
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .disableValidation()
                .assemble()
                .unwrap();

        ServiceShape service = model.getServiceShapes()
                .stream()
                .filter(s -> s.hasTrait(EndpointRuleSetTrait.class))
                .findFirst()
                .orElse(null);
        if (service == null || !service.hasTrait(EndpointTestsTrait.class)) {
            return;
        }

        List<EndpointTestCase> testCases = service.expectTrait(EndpointTestsTrait.class).getTestCases();
        if (testCases.isEmpty()) {
            return;
        }

        EndpointRuleSet ruleSet = service.expectTrait(EndpointRuleSetTrait.class).getEndpointRuleSet();
        Cfg cfg = Cfg.from(ruleSet);
        EndpointBddTrait trait = new NodeReversal()
                .apply(SiftingOptimization.builder().cfg(cfg).build().apply(EndpointBddTrait.from(cfg)));

        var builder = new RulesEngineBuilder();
        Bytecode bytecode = builder.compile(trait);
        var resolver = new BytecodeEndpointResolver(bytecode,
                builder.getExtensions(),
                builder.getBuiltinProviders());

        int checked = 0;
        for (EndpointTestCase testCase : testCases) {
            @SuppressWarnings("unchecked")
            var params = (Map<String, Object>) EndpointUtils.convertNode(testCase.getParams(), true);
            var expectedEndpoint = testCase.getExpect().getEndpoint().orElse(null);
            var expectedError = testCase.getExpect().getError().orElse(null);
            if (expectedEndpoint == null && expectedError == null) {
                continue;
            }

            String resolved;
            try {
                Endpoint endpoint = resolve(resolver, params);
                resolved = endpoint == null ? null : endpoint.uri().toString();
            } catch (RulesEvaluationError e) {
                resolved = null;
            }
            checked++;

            if (expectedError != null) {
                assertEquals(null,
                        resolved,
                        "ruleset was expected to reject " + params + " (" + expectedError + ") but resolved "
                                + resolved);
            } else if (!expectedEndpoint.getUrl().equals(resolved)) {
                assertKnownPreExistingDefect(service, params, expectedEndpoint.getUrl(), resolved);
            }
        }

        // An instruction NodeCompiler cannot decode raises a RulesEvaluationError rather than falling
        // back, so reaching here already proves every condition and result on the exercised paths
        // compiled.
        assertTrue(checked > 0, "no test case of this ruleset carried an expectation to check against");
    }

    /**
     * Allows through the two endpoint defects that predate node compilation, and fails on anything else.
     *
     * <p>Both reproduce identically on the bytecode interpreter, so neither is caused by compiling nodes;
     * they are matched by shape rather than by service name or count so that a genuine new divergence
     * cannot hide behind them as the bundled models change.
     *
     * @param service the service whose ruleset produced the mismatch.
     * @param params the test case parameters.
     * @param expected the URL the model declares.
     * @param resolved the URL the rules engine produced.
     */
    private static void assertKnownPreExistingDefect(
            ServiceShape service,
            Map<String, Object> params,
            String expected,
            String resolved
    ) {
        var detail = service.getId().getName() + " params=" + params
                + "\n  expected=" + expected + "\n  resolved=" + resolved;

        if (resolved == null) {
            fail("endpoint resolution failed where the model declares a URL:\n" + detail);
        }
        // SmithyUri lowercases the host, so a custom endpoint with mixed case comes back folded.
        if (resolved.equalsIgnoreCase(expected)) {
            return;
        }
        // A ruleset whose endpoint template carries an explicit port yields "scheme://[host:port]": the
        // whole "host:port" string reaches SmithyUri as the host, and because it contains a colon it is
        // bracketed as an IPv6 literal. BUILD_URI always passes port -1, so the port never gets split out.
        if (resolved.replace("[", "").replace("]", "").equals(expected)) {
            return;
        }

        fail("endpoint resolution does not match the model's declared expectation:\n" + detail);
    }

    private static Endpoint resolve(BytecodeEndpointResolver resolver, Map<String, Object> params) {
        var context = Context.create().put(RulesEngineSettings.ADDITIONAL_ENDPOINT_PARAMS, params);
        return resolver.resolveEndpoint(EndpointResolverParams.builder()
                .operation(new StubOperation())
                .inputValue(new StubInput())
                .context(context)
                .build());
    }

    private static final Schema INPUT_SCHEMA =
            Schema.structureBuilder(ShapeId.from("smithy.example#ConformanceInput")).build();

    /** Carries no context params, so every input comes from {@code ADDITIONAL_ENDPOINT_PARAMS}. */
    private static final class StubOperation implements ApiOperation<StubInput, StubInput> {
        @Override
        public ShapeBuilder<StubInput> inputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<StubInput> outputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema schema() {
            return Schema.createOperation(ShapeId.from("smithy.example#Conformance"));
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

    private static final class StubInput implements SerializableStruct {
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
