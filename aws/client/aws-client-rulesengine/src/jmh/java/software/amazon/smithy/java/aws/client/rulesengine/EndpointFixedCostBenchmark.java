/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.util.List;
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
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.endpoints.EndpointResolverParams;
import software.amazon.smithy.java.rulesengine.BytecodeEndpointResolver;
import software.amazon.smithy.java.rulesengine.RulesEngineBuilder;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.logic.bdd.NodeReversal;
import software.amazon.smithy.rulesengine.logic.cfg.Cfg;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;

/**
 * Measures what an endpoint resolution costs before any ruleset-specific work happens.
 *
 * <p>Comparing a real ruleset against {@code constant} — one parameter, no conditions, a hardcoded URL —
 * splits a resolve into the fixed per-call overhead every resolution pays (building the register array
 * from the template, filling builtins from the context, acquiring and releasing a pooled evaluator,
 * walking to a terminal, and constructing the {@code Endpoint}) and the part that actually depends on
 * what the ruleset says.
 *
 * <p>This exists because the per-call overhead turned out to dominate, and nothing inside the evaluator
 * could be optimized to reach parity with a generated resolver that is handed pre-built typed parameters.
 * Knowing the floor tells you which side of the boundary a change has to be on to matter.
 *
 * <ul>
 *   <li>{@code constant} — no conditions, constant URL. The floor.</li>
 *   <li>{@code template} — no conditions, URL interpolated from the region. Adds template resolution and
 *       URI construction, so the delta from {@code constant} is what building a URL costs.</li>
 *   <li>{@code oneCondition} — a single {@code isSet} condition on top of {@code template}, so the delta
 *       is roughly the marginal cost of one BDD node plus one condition node.</li>
 * </ul>
 */
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class EndpointFixedCostBenchmark {

    private static final String CONSTANT = """
            {
              "version": "1.0",
              "parameters": {
                "Region": {"type": "string", "builtIn": "AWS::Region", "required": true,
                           "documentation": "region"}
              },
              "rules": [
                {"conditions": [], "endpoint": {"url": "https://example.amazonaws.com"}, "type": "endpoint"}
              ]
            }
            """;

    private static final String TEMPLATE = """
            {
              "version": "1.0",
              "parameters": {
                "Region": {"type": "string", "builtIn": "AWS::Region", "required": true,
                           "documentation": "region"}
              },
              "rules": [
                {"conditions": [], "endpoint": {"url": "https://svc.{Region}.amazonaws.com"},
                 "type": "endpoint"}
              ]
            }
            """;

    private static final String ONE_CONDITION = """
            {
              "version": "1.0",
              "parameters": {
                "Region": {"type": "string", "builtIn": "AWS::Region", "required": true,
                           "documentation": "region"},
                "Bucket": {"type": "string", "required": false, "documentation": "bucket"}
              },
              "rules": [
                {"conditions": [{"fn": "isSet", "argv": [{"ref": "Bucket"}]}],
                 "endpoint": {"url": "https://{Bucket}.svc.{Region}.amazonaws.com"}, "type": "endpoint"},
                {"conditions": [], "endpoint": {"url": "https://svc.{Region}.amazonaws.com"},
                 "type": "endpoint"}
              ]
            }
            """;

    /**
     * Control for the single-operation rulesets below: same three-part URL they build, but no condition.
     * Without it their deltas from {@link #TEMPLATE} would include the extra URL part, overstating what
     * the operation itself costs.
     */
    private static final String TEMPLATE_LONG = """
            {
              "version": "1.0",
              "parameters": {
                "Region": {"type": "string", "builtIn": "AWS::Region", "required": true,
                           "documentation": "region"},
                "Bucket": {"type": "string", "required": true, "default": "my-bucket-name",
                           "documentation": "bucket"}
              },
              "rules": [
                {"conditions": [],
                 "endpoint": {"url": "https://{Bucket}.svc.{Region}.amazonaws.com"}, "type": "endpoint"}
              ]
            }
            """;

    // Each of the following adds exactly one rules-language operation on top of TEMPLATE, and supplies
    // its input through a parameter default so that parameter passing is identical across every ruleset
    // and the delta from TEMPLATE is the operation's own cost.
    private static final String PARTITION = """
            {
              "version": "1.0",
              "parameters": {
                "Region": {"type": "string", "builtIn": "AWS::Region", "required": true,
                           "documentation": "region"}
              },
              "rules": [
                {"conditions": [{"fn": "aws.partition", "argv": [{"ref": "Region"}], "assign": "p"}],
                 "endpoint": {"url": "https://svc.{Region}.{p#dnsSuffix}"}, "type": "endpoint"},
                {"conditions": [], "endpoint": {"url": "https://svc.{Region}.amazonaws.com"},
                 "type": "endpoint"}
              ]
            }
            """;

    private static final String HOST_LABEL = """
            {
              "version": "1.0",
              "parameters": {
                "Region": {"type": "string", "builtIn": "AWS::Region", "required": true,
                           "documentation": "region"},
                "Bucket": {"type": "string", "required": true, "default": "my-bucket-name",
                           "documentation": "bucket"}
              },
              "rules": [
                {"conditions": [{"fn": "isValidHostLabel",
                                 "argv": [{"ref": "Bucket"}, false]}],
                 "endpoint": {"url": "https://{Bucket}.svc.{Region}.amazonaws.com"}, "type": "endpoint"},
                {"conditions": [], "endpoint": {"url": "https://svc.{Region}.amazonaws.com"},
                 "type": "endpoint"}
              ]
            }
            """;

    private static final String VIRTUAL_HOSTABLE = """
            {
              "version": "1.0",
              "parameters": {
                "Region": {"type": "string", "builtIn": "AWS::Region", "required": true,
                           "documentation": "region"},
                "Bucket": {"type": "string", "required": true, "default": "my-bucket-name",
                           "documentation": "bucket"}
              },
              "rules": [
                {"conditions": [{"fn": "aws.isVirtualHostableS3Bucket",
                                 "argv": [{"ref": "Bucket"}, false]}],
                 "endpoint": {"url": "https://{Bucket}.svc.{Region}.amazonaws.com"}, "type": "endpoint"},
                {"conditions": [], "endpoint": {"url": "https://svc.{Region}.amazonaws.com"},
                 "type": "endpoint"}
              ]
            }
            """;

    private static final String SUBSTRING = """
            {
              "version": "1.0",
              "parameters": {
                "Region": {"type": "string", "builtIn": "AWS::Region", "required": true,
                           "documentation": "region"},
                "Bucket": {"type": "string", "required": true, "default": "my-bucket-name--x-s3",
                           "documentation": "bucket"}
              },
              "rules": [
                {"conditions": [{"fn": "stringEquals", "argv": [
                                   {"fn": "coalesce", "argv": [
                                      {"fn": "substring", "argv": [{"ref": "Bucket"}, 0, 6, true]}, ""]},
                                   "--x-s3"]}],
                 "endpoint": {"url": "https://{Bucket}.svc.{Region}.amazonaws.com"}, "type": "endpoint"},
                {"conditions": [], "endpoint": {"url": "https://svc.{Region}.amazonaws.com"},
                 "type": "endpoint"}
              ]
            }
            """;

    @State(Scope.Benchmark)
    public static class ResolverState {
        @Param({"constant",
                "template",
                "oneCondition",
                "templateLong",
                "partition",
                "hostLabel",
                "virtualHostable",
                "substring"})
        private String ruleset;

        EndpointResolver resolver;
        EndpointResolverParams params;

        @Setup
        public void setup() {
            String json = switch (ruleset) {
                case "constant" -> CONSTANT;
                case "template" -> TEMPLATE;
                case "oneCondition" -> ONE_CONDITION;
                case "templateLong" -> TEMPLATE_LONG;
                case "partition" -> PARTITION;
                case "hostLabel" -> HOST_LABEL;
                case "virtualHostable" -> VIRTUAL_HOSTABLE;
                case "substring" -> SUBSTRING;
                default -> throw new IllegalArgumentException(ruleset);
            };
            var ruleSet = EndpointRuleSet.fromNode(Node.parse(json));
            ruleSet.typeCheck();
            var cfg = Cfg.from(ruleSet);
            var trait = new NodeReversal().apply(EndpointBddTrait.from(cfg));
            var engine = new RulesEngineBuilder();
            var bytecode = engine.compile(trait);
            resolver = new BytecodeEndpointResolver(bytecode,
                    engine.getExtensions(),
                    engine.getBuiltinProviders());

            // The region arrives through the typed context key the builtin reads, as it would from a
            // generated client. Nothing is passed by name.
            var context = Context.create();
            context.put(RegionSetting.REGION, "us-west-2");
            params = EndpointResolverParams.builder()
                    .context(context)
                    .inputValue(new NullStruct())
                    .operation(new NullOperation())
                    .build();

            System.out.println("[fixed-cost] " + ruleset + " -> "
                    + resolver.resolveEndpoint(params).uri());
        }
    }

    @Benchmark
    public Object resolve(ResolverState state) {
        return state.resolver.resolveEndpoint(state.params);
    }

    private static final Schema INPUT_SCHEMA =
            Schema.structureBuilder(ShapeId.from("smithy.example#FixedCostInput")).build();
    // Held in a field, not rebuilt per call: ContextProvider reads operation.schema().id() on every
    // resolve, so building a Schema inside schema() would measure the benchmark stub, not the resolver.
    private static final Schema OPERATION_SCHEMA = Schema.createOperation(ShapeId.from("smithy.example#FixedCost"));

    /** Carries no context params, so the only input is the region builtin. */
    private static final class NullOperation implements ApiOperation<NullStruct, NullStruct> {
        @Override
        public ShapeBuilder<NullStruct> inputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeBuilder<NullStruct> outputBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema schema() {
            return OPERATION_SCHEMA;
        }

        @Override
        public Schema inputSchema() {
            return INPUT_SCHEMA;
        }

        @Override
        public Schema outputSchema() {
            return INPUT_SCHEMA;
        }

        @Override
        public TypeRegistry errorRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ShapeId> effectiveAuthSchemes() {
            return List.of();
        }

        @Override
        public List<Schema> errorSchemas() {
            return List.of();
        }

        @Override
        public ApiService service() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NullStruct implements SerializableStruct {
        @Override
        public Schema schema() {
            return INPUT_SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }
}
