/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
                            "Endpoint",
                            "https://example.com",
                            "UseFIPS",
                            false)),
            Map.entry("minimal-ruleset.json-1", Map.of("Region", "us-east-1")));

    @Param({
            "yes",
            "no",
    })
    private String optimize;

    @Param({
            "example-complex-ruleset.json-1",
            "minimal-ruleset.json-1"
    })
    private String testName;

    private EndpointRuleSet ruleSet;
    private Map<String, Object> parameters;
    private RulesProgram program;
    private Context ctx;

    @Setup
    public void setup() throws Exception {
        parameters = new HashMap<>(CASES.get(testName));
        var actualFile = testName.substring(0, testName.length() - 2);
        var url = VmBench.class.getResource(actualFile);
        if (url == null) {
            throw new RuntimeException("Test case not found: " + actualFile);
        }
        var data = Node.parse(IoUtils.readUtf8Url(url));
        ruleSet = EndpointRuleSet.fromNode(data);

        var engine = new RulesEngine();
        if (optimize.equals("nop")) {
            engine.disableOptimizations();
        }
        program = engine.compile(ruleSet);
        ctx = Context.create();
    }

    //    @Benchmark
    //    public Object compile() {
    //        // TODO
    //        return null;
    //    }

    @Benchmark
    public Object evaluate() {
        return program.resolveEndpoint(ctx, parameters);
    }
}
