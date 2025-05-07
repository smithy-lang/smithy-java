/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.traits.EndpointTestsTrait;
import software.amazon.smithy.utils.IoUtils;

public class RulesCompilerTest {
    @Test
    public void compilesMinimalRuleset() {
        var contents = IoUtils.readUtf8Resource(getClass(), "example-complex-ruleset.json");
        var data = Node.parse(contents);
        var ruleSet = EndpointRuleSet.fromNode(data);
        var engine = new RulesEngine();
        engine.addBuiltinProvider((n, c) -> {
            if (n.equals("AWS::Region")) {
                return "us-east-1";
            } else {
                return null;
            }
        });
        var program = engine.compile(ruleSet);

        System.out.println(program);

        System.out.println(program.resolveEndpoint(Context.create(),
                Map.of(
                        "TestCaseId",
                        "123",
                        "Input",
                        "345",
                        "Region",
                        "us-east-1",
                        "UseFIPS",
                        false,
                        "Endpoint",
                        "https://example.com")));
    }

    @ParameterizedTest
    @MethodSource("testCaseProvider")
    public void testRunner(Path modelFile) {
        var model = Model.assembler()
                .discoverModels()
                .addImport(modelFile)
                .assemble()
                .unwrap();
        var service = model.expectShape(ShapeId.from("example#FizzBuzz"), ServiceShape.class);
        var plugin = EndpointRulesPlugin.from(service);
        var testCases = service.expectTrait(EndpointTestsTrait.class);

        for (var test : testCases.getTestCases()) {
            var testParams = test.getParams();
            var ctx = Context.create();
            Map<String, Object> input = new HashMap<>();
            for (var entry : testParams.getStringMap().entrySet()) {
                input.put(entry.getKey(), EndpointUtils.convertNodeInput(entry.getValue()));
            }
            var expected = test.getExpect();
            expected.getEndpoint().ifPresent(expectedEndpoint -> {
                var result = plugin.getProgram().resolveEndpoint(ctx, input);
                assertThat(result.uri().toString(), equalTo(expectedEndpoint.getUrl()));
                var actualHeaders = result.property(Endpoint.HEADERS);
                if (expectedEndpoint.getHeaders().isEmpty()) {
                    assertThat(actualHeaders, nullValue());
                } else {
                    assertThat(actualHeaders, equalTo(expectedEndpoint.getHeaders()));
                }
                // TODO: validate properties too.
            });
            expected.getError().ifPresent(expectedError -> {
                try {
                    var result = plugin.getProgram().resolveEndpoint(ctx, input);
                    Assertions.fail("Expected ruleset to fail: " + modelFile + " : " + test.getDocumentation()
                            + ", but resolved " + result);
                } catch (RulesEvaluationError e) {
                    // pass
                }
            });
        }
    }

    public static List<Path> testCaseProvider() throws Exception {
        List<Path> result = new ArrayList<>();
        var baseUri = RulesCompilerTest.class.getResource("runner").toURI();
        var basePath = Paths.get(baseUri);
        for (var file : Objects.requireNonNull(basePath.toFile().listFiles())) {
            if (!file.isDirectory()) {
                result.add(file.toPath());
            }
        }
        return result;
    }
}
