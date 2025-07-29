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
import software.amazon.smithy.java.aws.client.core.settings.EndpointSettings;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.client.rulesengine.EndpointRulesPlugin;
import software.amazon.smithy.java.client.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.transform.ModelTransformer;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class Bench {
    private DynamicClient client;
    private EndpointResolver endpointResolver;
    private EndpointResolverParams endpointParams;

    private Model model;
    private ServiceShape service;
    private final RulesEngineBuilder engine = new RulesEngineBuilder();

    @Setup
    public void setup() {
        model = Model.assembler()
                .discoverModels()
                .addImport(ResolverTest.class.getResource("s3.json"))
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();
        model = customizeS3Model(model);
        service = model.expectShape(ShapeId.from("com.amazonaws.s3#AmazonS3"), ServiceShape.class);

        var plugin = EndpointRulesPlugin.create(engine);

        client = DynamicClient.builder()
                .model(model)
                .service(service.getId())
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .addPlugin(plugin)
                .build();
        endpointResolver = client.config().endpointResolver();

        var inputValue = client.createStruct(ShapeId.from("com.amazonaws.s3#GetObjectRequest"),
                Document.of(Map.of(
                        "Bucket",
                        Document.of("miked"),
                        "Key",
                        Document.of("bar"))));

        var ctx = Context.create();
        ctx.put(EndpointSettings.REGION, "us-east-1");
        //        ctx.put(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS, Map.of("UseFIPS", true, "UseDualStack", true));

        endpointParams = EndpointResolverParams.builder()
                .context(ctx)
                .inputValue(inputValue)
                .operation(client.getOperation("GetObject"))
                .build();
    }

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

    @Benchmark
    public Object evaluate() throws Exception {
        return endpointResolver.resolveEndpoint(endpointParams).get();
    }

    //    public static final TraitKey<EndpointRuleSetTrait> ENDPOINT_RULESET_TRAIT =
    //            TraitKey.get(EndpointRuleSetTrait.class);
    //
    //    @Benchmark
    //    public Object compileBdd() {
    //        var ruleset = client.config().service().schema().getTrait(ENDPOINT_RULESET_TRAIT);
    //        var cfg = Cfg.from(ruleset.getEndpointRuleSet());
    //        var bdd = BddTrait.from(cfg).transform(SiftingOptimization.builder().cfg(cfg).build()).transform(new NodeReversal());
    //        var bytecode = engine.compile(bdd);
    //        var providers = engine.getBuiltinProviders();
    //        return new BytecodeEndpointResolver(bytecode, engine.getExtensions(), providers);
    //    }
}
