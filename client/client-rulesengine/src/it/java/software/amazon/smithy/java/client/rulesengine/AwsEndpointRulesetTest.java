/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
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
 * <p>Set the {@code API_MODELS_AWS_DIR} environment variable to the root of a local
 * checkout of api-models-aws to enable this test.
 */
class AwsEndpointRulesetTest {

    private static Path getModelsDir() {
        var dir = System.getenv("API_MODELS_AWS_DIR");
        return dir != null ? Paths.get(dir, "models") : null;
    }

    static boolean modelsAvailable() {
        var dir = getModelsDir();
        return dir != null && Files.isDirectory(dir);
    }

    static Stream<Path> awsModels() throws IOException {
        var modelsDir = getModelsDir();
        try (var paths = Files.find(modelsDir,
                4,
                (p, a) -> p.toString().endsWith(".json")
                        && p.getParent().getParent().getFileName().toString().equals("service"))) {
            return paths.sorted().toList().stream();
        }
    }

    @EnabledIf("modelsAvailable")
    @ParameterizedTest(name = "{0}")
    @MethodSource("awsModels")
    void compileEvaluateAndRoundTrip(Path modelPath) {
        // Load the model
        Model model = Model.assembler()
                .addImport(modelPath)
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .discoverModels()
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
