/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.core.settings.S3EndpointSettings;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointContext;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.rulesengine.EndpointRulesPlugin;
import software.amazon.smithy.java.client.rulesengine.EndpointUtils;
import software.amazon.smithy.java.client.rulesengine.RulesEvaluationError;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.rulesengine.traits.EndpointTestCase;
import software.amazon.smithy.rulesengine.traits.EndpointTestsTrait;

public class ResolverTest {

    private static Model model;
    private static ServiceShape service;
    private static EndpointRulesPlugin plugin;
    private static DynamicClient client;

    // S3 requires a customization to remove buckets from the path :(
    private static Model customizeS3Model(Model m) {
        return ModelTransformer.create().mapShapes(m, s -> {
            if (s.isOperationShape()) {
                var httpTrait = s.getTrait(HttpTrait.class).orElse(null);
                if (httpTrait != null && httpTrait.getUri().getLabel("Bucket").isPresent()) {
                    // Remove the bucket from the URI pattern.
                    var uriString = httpTrait.getUri().toString().replace("{Bucket}", "");
                    uriString = uriString.replace("//", "/");
                    var newUri = UriPattern.parse(uriString);
                    var newHttpTrait = httpTrait.toBuilder().uri(newUri).build();
                    return Shape.shapeToBuilder(s).addTrait(newHttpTrait).build();
                }
            }
            return s;
        });
    }

    @BeforeAll
    public static void before() throws Exception {
        model = Model.assembler()
                .discoverModels()
                .addImport(ResolverTest.class.getResource("s3.json"))
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();
        model = customizeS3Model(model);
        service = model.expectShape(ShapeId.from("com.amazonaws.s3#AmazonS3"), ServiceShape.class);
        plugin = EndpointRulesPlugin.create();
        client = DynamicClient.builder()
                .model(model)
                .service(service.getId())
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .addPlugin(plugin)
                .build();
    }

    @ParameterizedTest
    @MethodSource("s3TestCases")
    public void caseRunner(EndpointTestCase test) {
        var expected = test.getExpect();
        var expectedError = expected.getError().orElse(null);
        var expectedEndpoint = expected.getEndpoint().orElse(null);
        try {
            var result = resolveEndpoint(test, client);
            if (expectedError != null) {
                Assertions.fail("Expected ruleset to fail: " + test.getDocumentation() + " : " + expectedError);
            }

            Endpoint ep = (Endpoint) result[0];
            assertThat(ep.uri().toString(), equalTo(expectedEndpoint.getUrl()));
            var actualHeaders = ep.property(EndpointContext.HEADERS);
            if (expectedEndpoint.getHeaders().isEmpty()) {
                assertThat(actualHeaders, nullValue());
            } else {
                assertThat(actualHeaders, equalTo(expectedEndpoint.getHeaders()));
            }
            // TODO: validate properties too.
        } catch (RulesEvaluationError e) {
            if (expectedError == null) {
                Assertions.fail("Expected ruleset to succeed: " + test.getDocumentation() + " : " + e, e);
            }
        }
    }

    public static List<EndpointTestCase> s3TestCases() {
        List<EndpointTestCase> tests = new ArrayList<>();
        for (var test : service.expectTrait(EndpointTestsTrait.class).getTestCases()) {
            if (test.getOperationInputs() != null && !test.getOperationInputs().isEmpty()) {
                // How do we test when there's no operation?
                tests.add(test);
            }
        }
        return tests;
    }

    private Object[] resolveEndpoint(EndpointTestCase test, DynamicClient client) {
        // The rules have operation input params, so simulate sending an operation.
        var resolvedEndpoint = new Object[2];
        var override = RequestOverrideConfig.builder()
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
                        resolvedEndpoint[0] = hook.context().get(CallContext.ENDPOINT);
                        hook.mapRequest(HttpRequest.class, r -> {
                            resolvedEndpoint[1] = r.request().uri();
                            return r.request();
                        });
                        throw new RulesEvaluationError("foo");
                    }
                })
                .putConfig(RegionSetting.REGION, "us-east-1");

        var inputs = test.getOperationInputs().get(0);
        var name = inputs.getOperationName();
        var inputParams = EndpointUtils.convertNode(inputs.getOperationParams(), true);

        if (!inputs.getBuiltInParams().isEmpty()) {
            inputs.getBuiltInParams().getStringMember("SDK::Endpoint").ifPresent(value -> {
                override.putConfig(CallContext.ENDPOINT, Endpoint.builder().uri(value.getValue()).build());
            });
            inputs.getBuiltInParams().getStringMember("AWS::Region").ifPresent(value -> {
                override.putConfig(RegionSetting.REGION, value.getValue());
            });
        }

        inputs.getOperationParams().getStringMember("Region").ifPresent(value -> {
            override.putConfig(RegionSetting.REGION, value.getValue());
        });

        inputs.getOperationParams().getBooleanMember("UseFIPS").ifPresent(value -> {
            override.putConfig(S3EndpointSettings.USE_FIPS, value.getValue());
        });

        inputs.getOperationParams().getBooleanMember("UseDualStack").ifPresent(value -> {
            override.putConfig(S3EndpointSettings.USE_DUAL_STACK, value.getValue());
        });

        inputs.getOperationParams().getBooleanMember("Accelerate").ifPresent(value -> {
            override.putConfig(S3EndpointSettings.S3_ACCELERATE, value.getValue());
        });

        inputs.getOperationParams().getBooleanMember("DisableMultiRegionAccessPoints").ifPresent(value -> {
            override.putConfig(S3EndpointSettings.S3_DISABLE_MULTI_REGION_ACCESS_POINTS, value.getValue());
        });

        inputs.getOperationParams().getBooleanMember("ForcePathStyle").ifPresent(value -> {
            override.putConfig(S3EndpointSettings.S3_FORCE_PATH_STYLE, value.getValue());
        });

        override.putConfig(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS,
                (Map<String, Object>) EndpointUtils.convertNode(test.getParams(), true));

        try {
            var document = Document.ofObject(inputParams);
            client.call(name, document, override.build());
            throw new RuntimeException("Expected exception");
        } catch (RulesEvaluationError e) {
            if (e.getMessage().equals("foo")) {
                return resolvedEndpoint;
            } else {
                throw e;
            }
        }
    }
}
