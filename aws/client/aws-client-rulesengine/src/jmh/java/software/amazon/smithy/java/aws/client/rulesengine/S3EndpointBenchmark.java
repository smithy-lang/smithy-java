/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
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
import software.amazon.smithy.java.rulesengine.Bytecode;
import software.amazon.smithy.java.rulesengine.BytecodeEndpointResolver;
import software.amazon.smithy.java.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.java.rulesengine.RulesEngineSettings;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.rulesengine.aws.s3.S3TreeRewriter;
import software.amazon.smithy.rulesengine.logic.bdd.CostOptimization;
import software.amazon.smithy.rulesengine.logic.bdd.NodeReversal;
import software.amazon.smithy.rulesengine.logic.bdd.SiftingOptimization;
import software.amazon.smithy.rulesengine.logic.cfg.Cfg;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class S3EndpointBenchmark {

    /**
     * Shared across all JMH forks. The compiled BDD bytecode is cached to temp files so the expensive
     * sifting optimization (~5s) only runs once across all forked JVMs.
     */
    private static class SharedResolver {
        private static final Path CACHE_DIR = Path.of(System.getProperty("java.io.tmpdir"));

        static final EndpointResolver RESOLVER;
        static final EndpointResolver REWRITTEN_RESOLVER;
        static final DynamicClient CLIENT;
        static final ApiOperation<?, ?> GET_OBJECT;

        static {
            Model model = Model.assembler()
                    .discoverModels()
                    .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                    .assemble()
                    .unwrap();
            model = customizeS3Model(model);

            ServiceShape service = model.expectShape(ShapeId.from("com.amazonaws.s3#AmazonS3"), ServiceShape.class);
            RulesEngineBuilder engine = new RulesEngineBuilder();

            CLIENT = DynamicClient.builder()
                    .model(model)
                    .serviceId(service.getId())
                    .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                    .putConfig(RulesEngineSettings.RULES_ENGINE_BUILDER, engine)
                    .addPlugin(new EndpointRulesPlugin())
                    .build();

            var extensions = engine.getExtensions();
            var builtins = engine.getBuiltinProviders();

            RESOLVER = new BytecodeEndpointResolver(
                    loadOrCompileBytecode(service, engine, false),
                    extensions,
                    builtins);
            REWRITTEN_RESOLVER = new BytecodeEndpointResolver(
                    loadOrCompileBytecode(service, engine, true),
                    extensions,
                    builtins);

            GET_OBJECT = CLIENT.getOperation("GetObject");
        }

        private static Bytecode loadOrCompileBytecode(
                ServiceShape service,
                RulesEngineBuilder engine,
                boolean rewrite
        ) {
            var cacheName = rewrite ? "s3-endpoint-bytecode-rewritten.bin" : "s3-endpoint-bytecode.bin";
            var cache = CACHE_DIR.resolve(cacheName);
            try {
                if (Files.exists(cache)) {
                    return engine.load(Files.readAllBytes(cache));
                }
            } catch (IOException ignored) {
                // Fall through to compile
            }

            var ruleSetTrait = service.expectTrait(EndpointRuleSetTrait.class);
            var ruleSet = ruleSetTrait.getEndpointRuleSet();
            if (rewrite) {
                ruleSet = S3TreeRewriter.transform(ruleSet);
            }
            var cfg = Cfg.from(ruleSet);
            var bddTrait = EndpointBddTrait.from(cfg);
            var siftedTrait = SiftingOptimization.builder().cfg(cfg).build().apply(bddTrait);
            var costOptimizedTrait = CostOptimization.builder().cfg(cfg).build().apply(siftedTrait);
            var finalTrait = new NodeReversal().apply(costOptimizedTrait);
            var bytecode = engine.compile(finalTrait);

            try {
                Files.write(cache, bytecode.getBytecode());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to cache bytecode", e);
            }

            return bytecode;
        }

        private static Model customizeS3Model(Model model) {
            var transformer = ModelTransformer.create();
            Model m = transformer.removeTraitsIf(model, (shape, trait) -> trait instanceof StreamingTrait);
            return transformer.mapShapes(m, s -> {
                if (s.isOperationShape()) {
                    var httpTrait = s.getTrait(HttpTrait.class).orElse(null);
                    if (httpTrait != null && httpTrait.getUri().getLabel("Bucket").isPresent()) {
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
    }

    @State(Scope.Benchmark)
    public static class ParamState {
        @Param({"binding", "canned"})
        private String paramMode;

        @Param({"none", "rewritten"})
        private String treeMode;

        EndpointResolver resolver;
        EndpointResolverParams vanillaVirtualAddressingParams;
        EndpointResolverParams vanillaPathStyleParams;
        EndpointResolverParams dataPlaneShortZoneParams;
        EndpointResolverParams vanillaAccessPointArnParams;
        EndpointResolverParams s3OutpostsVanillaParams;

        @Setup
        public void setup() {
            boolean canned = "canned".equals(paramMode);
            resolver = "rewritten".equals(treeMode)
                    ? SharedResolver.REWRITTEN_RESOLVER
                    : SharedResolver.RESOLVER;
            var client = SharedResolver.CLIENT;
            var getObject = SharedResolver.GET_OBJECT;

            // vanilla virtual addressing@us-west-2
            vanillaVirtualAddressingParams = buildParams(client,
                    getObject,
                    canned,
                    Map.of("Bucket", Document.of("bucket-name"), "Key", Document.of("key")),
                    "us-west-2",
                    Map.of("Accelerate",
                            false,
                            "Bucket",
                            "bucket-name",
                            "ForcePathStyle",
                            false,
                            "Region",
                            "us-west-2",
                            "UseDualStack",
                            false,
                            "UseFIPS",
                            false));

            // vanilla path style@us-west-2
            vanillaPathStyleParams = buildParams(client,
                    getObject,
                    canned,
                    Map.of("Bucket", Document.of("bucket-name"), "Key", Document.of("key")),
                    "us-west-2",
                    Map.of("Accelerate",
                            false,
                            "Bucket",
                            "bucket-name",
                            "ForcePathStyle",
                            true,
                            "Region",
                            "us-west-2",
                            "UseDualStack",
                            false,
                            "UseFIPS",
                            false));

            // Data Plane with short zone name
            dataPlaneShortZoneParams = buildParams(client,
                    getObject,
                    canned,
                    Map.of("Bucket", Document.of("mybucket--abcd-ab1--x-s3"), "Key", Document.of("key")),
                    "us-east-1",
                    Map.of("Region",
                            "us-east-1",
                            "Bucket",
                            "mybucket--abcd-ab1--x-s3",
                            "UseFIPS",
                            false,
                            "UseDualStack",
                            false,
                            "Accelerate",
                            false,
                            "UseS3ExpressControlEndpoint",
                            false));

            // vanilla access point arn@us-west-2
            vanillaAccessPointArnParams = buildParams(client,
                    getObject,
                    canned,
                    Map.of("Bucket",
                            Document.of("arn:aws:s3:us-west-2:123456789012:accesspoint:myendpoint"),
                            "Key",
                            Document.of("key")),
                    "us-west-2",
                    Map.of("Accelerate",
                            false,
                            "Bucket",
                            "arn:aws:s3:us-west-2:123456789012:accesspoint:myendpoint",
                            "ForcePathStyle",
                            false,
                            "Region",
                            "us-west-2",
                            "UseDualStack",
                            false,
                            "UseFIPS",
                            false));

            // S3 outposts vanilla test
            s3OutpostsVanillaParams = buildParams(client,
                    getObject,
                    canned,
                    Map.of("Bucket",
                            Document.of(
                                    "arn:aws:s3-outposts:us-west-2:123456789012:outpost/op-01234567890123456/accesspoint/reports"),
                            "Key",
                            Document.of("key")),
                    "us-west-2",
                    Map.of("Region",
                            "us-west-2",
                            "UseFIPS",
                            false,
                            "UseDualStack",
                            false,
                            "Accelerate",
                            false,
                            "Bucket",
                            "arn:aws:s3-outposts:us-west-2:123456789012:outpost/op-01234567890123456/accesspoint/reports"));

            // Dump disassembly to tmp file for analysis
            dumpDisassembly(treeMode);
        }

        private void dumpDisassembly(String label) {
            try {
                var bytecode = ((BytecodeEndpointResolver) resolver).getBytecode();
                var path = SharedResolver.CACHE_DIR.resolve("s3-bytecode-" + label + ".txt");
                Files.writeString(path, bytecode.toString());
                System.out.println("[disasm] Wrote " + path);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static EndpointResolverParams buildParams(
                DynamicClient client,
                ApiOperation<?, ?> operation,
                boolean canned,
                Map<String, Document> inputMembers,
                String region,
                Map<String, Object> additionalParams
        ) {
            var inputValue = client.createStruct(
                    ShapeId.from("com.amazonaws.s3#GetObjectRequest"),
                    Document.of(canned ? Map.of() : inputMembers));
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
    public Object vanillaVirtualAddressing(ParamState params) {
        return params.resolver.resolveEndpoint(params.vanillaVirtualAddressingParams);
    }

    @Benchmark
    public Object vanillaPathStyle(ParamState params) {
        return params.resolver.resolveEndpoint(params.vanillaPathStyleParams);
    }

    @Benchmark
    public Object dataPlaneShortZone(ParamState params) {
        return params.resolver.resolveEndpoint(params.dataPlaneShortZoneParams);
    }

    @Benchmark
    public Object vanillaAccessPointArn(ParamState params) {
        return params.resolver.resolveEndpoint(params.vanillaAccessPointArnParams);
    }

    @Benchmark
    public Object s3OutpostsVanilla(ParamState params) {
        return params.resolver.resolveEndpoint(params.s3OutpostsVanillaParams);
    }

    /**
     * Cycles through different bucket names on each invocation to defeat the URI cache hot-slot.
     * This measures the cost of URI construction on cache miss.
     */
    @State(Scope.Thread)
    public static class VaryingBucketState {
        private static final int BUCKET_COUNT = 64;
        private EndpointResolverParams[] paramVariants;
        private int index;

        @Setup
        public void setup(ParamState params) {
            boolean canned = "canned".equals(params.paramMode);
            var client = SharedResolver.CLIENT;
            var getObject = SharedResolver.GET_OBJECT;
            paramVariants = new EndpointResolverParams[BUCKET_COUNT];
            for (int i = 0; i < BUCKET_COUNT; i++) {
                String bucket = "bucket-" + i;
                paramVariants[i] = ParamState.buildParams(client,
                        getObject,
                        canned,
                        Map.of("Bucket", Document.of(bucket), "Key", Document.of("key")),
                        "us-west-2",
                        Map.of("Accelerate",
                                false,
                                "Bucket",
                                bucket,
                                "ForcePathStyle",
                                false,
                                "Region",
                                "us-west-2",
                                "UseDualStack",
                                false,
                                "UseFIPS",
                                false));
            }
        }
    }

    @Benchmark
    public Object vanillaVirtualAddressingVaryingBucket(ParamState params, VaryingBucketState varying) {
        var p = varying.paramVariants[varying.index++ & (VaryingBucketState.BUCKET_COUNT - 1)];
        return params.resolver.resolveEndpoint(p);
    }
}
