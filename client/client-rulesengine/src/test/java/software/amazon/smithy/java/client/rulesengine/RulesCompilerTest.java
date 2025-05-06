/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
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
}
