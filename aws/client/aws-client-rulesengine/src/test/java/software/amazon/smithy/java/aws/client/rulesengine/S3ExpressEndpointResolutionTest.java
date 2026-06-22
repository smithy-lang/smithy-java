/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.rulesengine.EndpointRulesPlugin;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.java.rulesengine.RulesEngineSettings;
import software.amazon.smithy.java.rulesengine.RulesEvaluationError;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Reproduces the S3-Express benchmark routing failure: PutObject for a directory bucket like
 * {@code dowling-bench--use1-az4--x-s3} should resolve to the zonal endpoint
 * ({@code https://...s3express-use1-az4.us-east-1.amazonaws.com/...}) and the rules engine
 * should emit {@code aws.auth#sigv4S3express} as the chosen auth scheme.
 */
public class S3ExpressEndpointResolutionTest {

    private static final ShapeId S3_EXPRESS_SCHEME_ID = ShapeId.from("aws.auth#sigv4S3express");

    private static DynamicClient client;

    @BeforeAll
    public static void before() {
        Model model = Model.assembler()
                .discoverModels()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();
        model = customizeS3Model(model);
        ServiceShape service = model.expectShape(ShapeId.from("com.amazonaws.s3#AmazonS3"), ServiceShape.class);
        var engine = new RulesEngineBuilder();
        // Register a no-op scheme matching the id we expect the endpoint to ask for. Without
        // this, ClientPipeline.applyEndpointAuthSchemeOverrides throws when it can't find a
        // supported scheme for the endpoint-emitted name.
        AuthScheme<HttpRequest, Identity> noopScheme = AuthScheme.of(
                S3_EXPRESS_SCHEME_ID,
                HttpRequest.class,
                Identity.class,
                (request, identity, properties) -> new SignResult<>(request));
        client = DynamicClient.builder()
                .model(model)
                .serviceId(service.getId())
                .authSchemeResolver(params -> List.of(new AuthSchemeOption(S3_EXPRESS_SCHEME_ID)))
                .putSupportedAuthSchemes(noopScheme)
                .addIdentityResolver(new IdentityResolver<Identity>() {
                    @Override
                    public IdentityResult<Identity> resolveIdentity(Context requestProperties) {
                        return IdentityResult.of(new Identity() {});
                    }

                    @Override
                    public Class<Identity> identityType() {
                        return Identity.class;
                    }
                })
                .putConfig(RulesEngineSettings.RULES_ENGINE_BUILDER, engine)
                .addPlugin(new EndpointRulesPlugin())
                .build();
    }

    @Test
    public void resolvesS3ExpressZonalEndpointForDirectoryBucket() {
        var endpoint = resolve("GetObject",
                Map.of(
                        "Bucket",
                        "dowling-bench--use1-az4--x-s3",
                        "Key",
                        "objects/256KiB/1"));
        assertThat(endpoint.uri().toString(),
                containsString("dowling-bench--use1-az4--x-s3.s3express-use1-az4.us-east-1.amazonaws.com"));
    }

    @Test
    public void emitsSigv4S3expressAuthSchemeOnDirectoryBucketEndpoint() {
        var endpoint = resolve("GetObject",
                Map.of(
                        "Bucket",
                        "dowling-bench--use1-az4--x-s3",
                        "Key",
                        "objects/256KiB/1"));
        var schemeIds = new ArrayList<String>();
        for (var s : endpoint.authSchemes()) {
            schemeIds.add(s.authSchemeId());
        }
        assertThat(schemeIds.toString(),
                schemeIds,
                Matchers.contains(S3_EXPRESS_SCHEME_ID.toString()));
    }

    private Endpoint resolve(String operationName, Map<String, Object> input) {
        var captured = new Endpoint[1];
        var override = RequestOverrideConfig.builder()
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
                        captured[0] = hook.context().get(CallContext.ENDPOINT);
                        throw new RulesEvaluationError("captured");
                    }
                })
                .putConfig(RegionSetting.REGION, "us-east-1");

        try {
            client.call(operationName, Document.ofObject(input), override.build());
            Assertions.fail("Expected interceptor to throw");
        } catch (RulesEvaluationError e) {
            if (!"captured".equals(e.getMessage())) {
                throw e;
            }
        }
        return captured[0];
    }

    // Remove the streaming trait (not supported here). S3Plugin handles bucket-in-path removal.
    private static Model customizeS3Model(Model model) {
        return ModelTransformer.create()
                .removeTraitsIf(model, (shape, trait) -> trait instanceof StreamingTrait);
    }
}
