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
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.core.settings.S3EndpointSettings;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.rulesengine.EndpointRulesPlugin;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointContext;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.rulesengine.EndpointUtils;
import software.amazon.smithy.java.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.java.rulesengine.RulesEngineSettings;
import software.amazon.smithy.java.rulesengine.RulesEvaluationError;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.rulesengine.traits.EndpointTestCase;
import software.amazon.smithy.rulesengine.traits.EndpointTestsTrait;

public class ResolverTest {

    private static Model model;
    private static ServiceShape service;
    private static EndpointRulesPlugin plugin;
    private static DynamicClient client;

    private Map<String, Object> overrideMap = null;
    private Object inputParams = null;

    // Remove the streaming trait (not yet supported here). S3Plugin handles bucket-in-path removal.
    private static Model customizeS3Model(Model model) {
        return ModelTransformer.create()
                .removeTraitsIf(model, (shape, trait) -> trait instanceof StreamingTrait);
    }

    @BeforeAll
    public static void before() throws Exception {
        model = Model.assembler()
                .discoverModels()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();
        model = customizeS3Model(model);
        service = model.expectShape(ShapeId.from("com.amazonaws.s3#AmazonS3"), ServiceShape.class);
        var engine = new RulesEngineBuilder();
        plugin = new EndpointRulesPlugin();
        client = DynamicClient.builder()
                .model(model)
                .serviceId(service.getId())
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                // Endpoint rules in S3 emit aws.auth#sigv4 / sigv4a in the authSchemes property; the
                // pipeline now swaps to the endpoint-requested scheme if the client supports it. The
                // test uses NO_AUTH for resolution but registers stub schemes here so the swap can
                // succeed and the URL gets captured by the readBeforeTransmit interceptor.
                .putSupportedAuthSchemes(
                        new StubAuthScheme(ShapeId.from("aws.auth#sigv4")),
                        new StubAuthScheme(ShapeId.from("aws.auth#sigv4a")),
                        new StubAuthScheme(ShapeId.from("aws.auth#sigv4S3express")))
                .putConfig(RulesEngineSettings.RULES_ENGINE_BUILDER, engine)
                .addPlugin(plugin)
                .build();
    }

    /**
     * A no-op auth scheme that mirrors {@code smithy.api#noAuth} but with an arbitrary scheme ID.
     * Used by tests to satisfy endpoint-driven auth-scheme swapping without actually signing.
     */
    private record StubAuthScheme(ShapeId schemeId) implements AuthScheme<Object, Identity> {
        private static final IdentityResolver<Identity> NULL_RESOLVER = new IdentityResolver<>() {
            private static final IdentityResult<Identity> NULL_IDENTITY = IdentityResult.of(new Identity() {});

            @Override
            public IdentityResult<Identity> resolveIdentity(Context requestProperties) {
                return NULL_IDENTITY;
            }

            @Override
            public Class<Identity> identityType() {
                return Identity.class;
            }
        };

        @Override
        public Class<Object> requestClass() {
            return Object.class;
        }

        @Override
        public Class<Identity> identityClass() {
            return Identity.class;
        }

        @Override
        public IdentityResolver<Identity> identityResolver(IdentityResolvers resolvers) {
            return NULL_RESOLVER;
        }

        @Override
        public Signer<Object, Identity> signer() {
            return Signer.nullSigner();
        }
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
                Assertions.fail("Expected ruleset to fail: " + test.getDocumentation()
                        + " : " + expectedError + " : but got " + result[0]);
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
                String msg = "Expected ruleset to succeed: " + test.getDocumentation();
                msg += " Input: " + inputParams + "\n";
                msg += " Overrides: " + overrideMap;
                Assertions.fail(msg, e);
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
                        if (hook.request() instanceof HttpRequest req) {
                            resolvedEndpoint[1] = req.uri();
                        }
                        throw new RulesEvaluationError("foo");
                    }
                })
                .putConfig(RegionSetting.REGION, "us-east-1");

        var inputs = test.getOperationInputs().get(0);
        var name = inputs.getOperationName();
        inputParams = EndpointUtils.convertNode(inputs.getOperationParams(), true);

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

        overrideMap = (Map<String, Object>) EndpointUtils.convertNode(test.getParams(), true);
        override.putConfig(RulesEngineSettings.ADDITIONAL_ENDPOINT_PARAMS, overrideMap);

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
