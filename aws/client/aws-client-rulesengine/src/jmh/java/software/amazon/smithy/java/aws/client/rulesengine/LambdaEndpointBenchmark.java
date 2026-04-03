/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.rulesengine.EndpointRulesPlugin;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.endpoints.EndpointResolverParams;
import software.amazon.smithy.java.rulesengine.BytecodeEndpointResolver;
import software.amazon.smithy.java.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.java.rulesengine.RulesEngineSettings;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class LambdaEndpointBenchmark {

    private static final ShapeId LAMBDA_SERVICE = ShapeId.from("com.amazonaws.lambda#AWSGirApiService");
    private static final ShapeId GET_ACCOUNT_SETTINGS_INPUT =
            ShapeId.from("com.amazonaws.lambda#GetAccountSettingsRequest");

    private static class SharedResolver {
        static final EndpointResolver RESOLVER;
        static final DynamicClient CLIENT;
        static final ApiOperation<?, ?> GET_ACCOUNT_SETTINGS;

        static {
            Model model = Model.assembler()
                    .discoverModels()
                    .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                    .assemble()
                    .unwrap();

            ServiceShape service = model.expectShape(LAMBDA_SERVICE, ServiceShape.class);
            RulesEngineBuilder engine = new RulesEngineBuilder();

            CLIENT = DynamicClient.builder()
                    .model(model)
                    .serviceId(LAMBDA_SERVICE)
                    .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                    .putConfig(RulesEngineSettings.RULES_ENGINE_BUILDER, engine)
                    .addPlugin(new EndpointRulesPlugin())
                    .build();

            // Compile endpointRuleSet through the full optimization pipeline
            var ruleSetTrait = service.expectTrait(EndpointRuleSetTrait.class);
            var bytecode = engine.compile(ruleSetTrait.getEndpointRuleSet());
            RESOLVER = new BytecodeEndpointResolver(
                    bytecode,
                    engine.getExtensions(),
                    engine.getBuiltinProviders());

            GET_ACCOUNT_SETTINGS = CLIENT.getOperation("GetAccountSettings");
        }
    }

    @State(Scope.Benchmark)
    public static class ParamState {
        EndpointResolverParams usEast1NoFipsNoDualStackParams;
        EndpointResolverParams usGovEast1FipsDualStackParams;

        @Setup
        public void setup() {
            var client = SharedResolver.CLIENT;
            var op = SharedResolver.GET_ACCOUNT_SETTINGS;

            // For region us-east-1 with FIPS disabled and DualStack disabled
            usEast1NoFipsNoDualStackParams = buildParams(client,
                    op,
                    "us-east-1",
                    Map.of("Region", "us-east-1", "UseFIPS", false, "UseDualStack", false));

            // For region us-gov-east-1 with FIPS enabled and DualStack enabled
            usGovEast1FipsDualStackParams = buildParams(client,
                    op,
                    "us-gov-east-1",
                    Map.of("Region", "us-gov-east-1", "UseFIPS", true, "UseDualStack", true));
        }

        private static EndpointResolverParams buildParams(
                DynamicClient client,
                ApiOperation<?, ?> operation,
                String region,
                Map<String, Object> additionalParams
        ) {
            var inputValue = client.createStruct(GET_ACCOUNT_SETTINGS_INPUT, Document.of(Map.of()));
            var ctx = Context.create();
            ctx.put(RegionSetting.REGION, region);
            ctx.put(RulesEngineSettings.ADDITIONAL_ENDPOINT_PARAMS, additionalParams);
            return EndpointResolverParams.builder()
                    .context(ctx)
                    .inputValue(inputValue)
                    .operation(operation)
                    .build();
        }
    }

    @Benchmark
    public Object usEast1NoFipsNoDualStack(ParamState params) {
        return SharedResolver.RESOLVER.resolveEndpoint(params.usEast1NoFipsNoDualStackParams);
    }

    @Benchmark
    public Object usGovEast1FipsDualStack(ParamState params) {
        return SharedResolver.RESOLVER.resolveEndpoint(params.usGovEast1FipsDualStackParams);
    }
}
