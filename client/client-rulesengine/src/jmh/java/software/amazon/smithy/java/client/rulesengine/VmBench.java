/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.java.client.rulesengine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.logic.bdd.Bdd;
import software.amazon.smithy.rulesengine.logic.bdd.BddEvaluator;
import software.amazon.smithy.rulesengine.logic.bdd.NodeReversal;
import software.amazon.smithy.rulesengine.logic.bdd.SiftingOptimization;
import software.amazon.smithy.rulesengine.logic.cfg.Cfg;
import software.amazon.smithy.utils.IoUtils;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(
        iterations = 2,
        time = 3,
        timeUnit = TimeUnit.SECONDS)
@Measurement(
        iterations = 3,
        time = 3,
        timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class VmBench {

    private static final Map<String, Map<String, Object>> CASES = Map.ofEntries(
            Map.entry("example-complex-ruleset.json-1",
                      Map.of(
                              "Endpoint", "https://example.com"
                              , "UseFIPS", Boolean.FALSE
                      )),
            Map.entry("minimal-ruleset.json-1", Map.of("Region", "us-east-1")));

    @Param({
            "example-complex-ruleset.json-1",
            "minimal-ruleset.json-1"
    })
    private String testName;

    private EndpointRuleSet ruleSet;
    private Map<String, Object> parameters;
    private Bytecode bytecode;
    private Bdd bdd;
    private Context ctx;
    private Map<String, Function<Context, Object>> builtinProviders;
    private RulesEngineBuilder engine;
    private BytecodeEvaluator bytecodeEvaluator;

    @Setup
    public void setup() {
        parameters = new HashMap<>(CASES.get(testName));
        var actualFile = testName.substring(0, testName.length() - 2);
        var url = VmBench.class.getResource(actualFile);
        if (url == null) {
            throw new RuntimeException("Test case not found: " + actualFile);
        }
        var data = Node.parse(IoUtils.readUtf8Url(url));
        ruleSet = EndpointRuleSet.fromNode(data);

        engine = new RulesEngineBuilder();
        builtinProviders = new HashMap<>();
        for (var ext : RulesEngineBuilder.EXTENSIONS) {
            ext.putBuiltinProviders(builtinProviders);
        }

        var cfg = Cfg.from(ruleSet);
        bdd = Bdd.from(cfg);
        bdd = bdd.transform(SiftingOptimization.builder().cfg(cfg).build()).transform(new NodeReversal());

        bytecode = engine.compile(bdd);
        ctx = Context.create();
        System.out.println(bytecode);

        bytecodeEvaluator = new BytecodeEvaluator(bytecode, ctx, parameters, builtinProviders, engine.getExtensions());
    }

    @Benchmark
    public Object evaluate() {
        return evaluateBytecode();
    }

    private Object evaluateBytecode() {
        int resultIndex = BddEvaluator.from(bdd).evaluate(bytecodeEvaluator);
        return bytecodeEvaluator.resolveResult(resultIndex);
    }
}
