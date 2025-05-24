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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointContext;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;
import software.amazon.smithy.rulesengine.traits.EndpointTestCase;
import software.amazon.smithy.rulesengine.traits.EndpointTestsTrait;

public class RulesCompilerTest {
    @ParameterizedTest
    @MethodSource("testCaseProvider")
    public void testRunner(Path modelFile) {
        var model = Model.assembler()
                .discoverModels()
                .addImport(modelFile)
                .assemble()
                .unwrap();
        var service = model.expectShape(ShapeId.from("example#FizzBuzz"), ServiceShape.class);
        var engine = new RulesEngine();
        var program = engine.compile(service.expectTrait(EndpointRuleSetTrait.class).getEndpointRuleSet());
        var plugin = EndpointRulesPlugin.from(program);
        var testCases = service.expectTrait(EndpointTestsTrait.class);

        var client = DynamicClient.builder()
                .model(model)
                .service(service.getId())
                .protocol(new AwsJson1Protocol(service.getId()))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .addPlugin(plugin)
                .build();

        for (var test : testCases.getTestCases()) {
            var testParams = test.getParams();
            var ctx = Context.create();
            Map<String, Object> input = new HashMap<>();
            for (var entry : testParams.getStringMap().entrySet()) {
                input.put(entry.getKey(), EndpointUtils.convertNode(entry.getValue()));
            }
            var expected = test.getExpect();
            expected.getEndpoint().ifPresent(expectedEndpoint -> {
                try {
                    var result = resolveEndpoint(test, client, plugin, ctx, input);
                    assertThat(result.uri().toString(), equalTo(expectedEndpoint.getUrl()));
                    var actualHeaders = result.property(EndpointContext.HEADERS);
                    if (expectedEndpoint.getHeaders().isEmpty()) {
                        assertThat(actualHeaders, nullValue());
                    } else {
                        assertThat(actualHeaders, equalTo(expectedEndpoint.getHeaders()));
                    }
                    // TODO: validate properties too.
                } catch (RulesEvaluationError e) {
                    Assertions.fail("Expected ruleset to succeed: "
                            + modelFile + " : "
                            + test.getDocumentation()
                            + " : " + e, e);
                }
            });
            expected.getError().ifPresent(expectedError -> {
                try {
                    var result = resolveEndpoint(test, client, plugin, ctx, input);
                    Assertions.fail("Expected ruleset to fail: " + modelFile + " : " + test.getDocumentation()
                            + ", but resolved " + result);
                } catch (RulesEvaluationError e) {
                    // pass
                }
            });
        }
    }

    private Endpoint resolveEndpoint(
            EndpointTestCase test,
            DynamicClient client,
            EndpointRulesPlugin plugin,
            Context ctx,
            Map<String, Object> input
    ) {
        // Supports a single operations inputs
        if (test.getOperationInputs().isEmpty()) {
            return plugin.getProgram().resolveEndpoint(ctx, input);
        }

        // The rules have operation input params, so simulate sending an operation.
        var inputs = test.getOperationInputs().get(0);
        var name = inputs.getOperationName();
        var inputParams = EndpointUtils.convertNode(inputs.getOperationParams(), true);
        var resolvedEndpoint = new Endpoint[1];
        var override = RequestOverrideConfig.builder()
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
                        resolvedEndpoint[0] = hook.context().get(CallContext.ENDPOINT);
                        throw new RulesEvaluationError("foo");
                    }
                });

        if (!inputs.getBuiltInParams().isEmpty()) {
            inputs.getBuiltInParams().getStringMember("SDK::Endpoint").ifPresent(value -> {
                override.putConfig(CallContext.ENDPOINT, Endpoint.builder().uri(value.getValue()).build());
            });
        }

        try {
            var document = Document.ofObject(inputParams);
            client.call(name, document, override.build());
            throw new RuntimeException("Expected exception");
        } catch (RulesEvaluationError e) {
            if (e.getMessage().equals("foo")) {
                return resolvedEndpoint[0];
            } else {
                throw e;
            }
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
