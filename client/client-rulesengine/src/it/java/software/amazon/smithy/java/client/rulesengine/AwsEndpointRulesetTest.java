/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.rulesengine.Bytecode;
import software.amazon.smithy.java.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.loader.ModelDiscovery;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.language.evaluation.TestEvaluator;
import software.amazon.smithy.rulesengine.logic.bdd.NodeReversal;
import software.amazon.smithy.rulesengine.logic.bdd.SiftingOptimization;
import software.amazon.smithy.rulesengine.logic.cfg.Cfg;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;
import software.amazon.smithy.rulesengine.traits.EndpointTestCase;
import software.amazon.smithy.rulesengine.traits.EndpointTestsTrait;

/**
 * Tests that every AWS service endpoint ruleset can be compiled to BDD bytecode,
 * evaluated against its endpoint test cases, round-tripped through serialization,
 * and disassembled without errors.
 *
 * <p>AWS service models are discovered on the classpath via
 * {@link ModelDiscovery#findModels()}.
 */
@EnabledIfSystemProperty(named = "awsModelsTests", matches = "true")
class AwsEndpointRulesetTest {

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
    void compileEvaluateAndRoundTrip(URL modelUrl) {
        // Load the model
        Model model = Model.assembler()
                .addImport(modelUrl)
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .disableValidation()
                .assemble()
                .unwrap();

        // Find the service with endpoint rules
        ServiceShape service = model.getServiceShapes()
                .stream()
                .filter(s -> s.hasTrait(EndpointRuleSetTrait.class))
                .findFirst()
                .orElse(null);
        if (service == null) {
            // Skip models without endpoint rules
            return;
        }

        EndpointRuleSet ruleSet = service.expectTrait(EndpointRuleSetTrait.class).getEndpointRuleSet();
        List<EndpointTestCase> testCases = service.hasTrait(EndpointTestsTrait.class)
                ? service.expectTrait(EndpointTestsTrait.class).getTestCases()
                : List.of();

        // Evaluate test cases against the original rules (if these fail, no point in testing the BDD)
        for (EndpointTestCase testCase : testCases) {
            TestEvaluator.evaluate(ruleSet, testCase);
        }

        // Compile to BDD
        Cfg cfg = Cfg.from(ruleSet);
        EndpointBddTrait bddTrait = EndpointBddTrait.from(cfg);
        EndpointBddTrait optimizedTrait = SiftingOptimization.builder().cfg(cfg).build().apply(bddTrait);
        EndpointBddTrait finalTrait = new NodeReversal().apply(optimizedTrait);

        // Evaluate test cases against the BDD
        for (EndpointTestCase testCase : testCases) {
            TestEvaluator.evaluate(finalTrait, testCase);
        }

        // Compile BDD to bytecode
        var builder = new RulesEngineBuilder();
        Bytecode compiled = builder.compile(finalTrait);
        assertNotNull(compiled);
        assertTrue(compiled.getBytecode().length > 0);

        // Round-trip: serialize then load
        byte[] serialized = compiled.getBytecode();
        Bytecode loaded = builder.load(serialized);
        assertArrayEquals(serialized, loaded.getBytecode(), "Round-trip mismatch");

        // Disassemble without error
        String disassembly = loaded.toString();
        assertTrue(disassembly.contains("=== Bytecode Program ==="));
    }
}
