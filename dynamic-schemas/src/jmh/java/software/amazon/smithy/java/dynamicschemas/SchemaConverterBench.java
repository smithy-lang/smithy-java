/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class SchemaConverterBench {

    private static final String[] PRIM_TYPES = {"String", "Integer", "Boolean", "Long", "Double"};

    @Param({"small", "medium", "large"})
    String modelSize;

    Model model;
    List<Shape> structureShapes;
    Shape recursiveShape;
    SchemaConverter preWarmedConverter;

    /**
     * Defines a group of structures with uniform shape.
     *
     * @param count      number of structures in this tier
     * @param primCount  number of primitive members per structure (cycles through String/Integer/Boolean/Long/Double)
     * @param refsPerTier number of structure refs from each struct to tier k (index k -> count of refs to tier k)
     * @param fwdSteps   forward refs within the same tier — each value is a step size to a higher-indexed struct,
     *                   creating a DAG that forces DFS to re-explore shared subgraphs through distinct paths
     */
    private record Tier(int count, int primCount, List<Integer> refsPerTier, List<Integer> fwdSteps) {}

    @Setup
    public void setup() {
        String smithyModel = buildModel(modelSize);
        model = Model.assembler()
                .addUnparsedModel("bench.smithy", smithyModel)
                .assemble()
                .unwrap();

        structureShapes = new ArrayList<>();
        for (var shape : model.toSet()) {
            if (shape.getType() == ShapeType.STRUCTURE
                    && shape.getId().getNamespace().equals("smithy.bench")) {
                structureShapes.add(shape);
            }
        }

        if ("large".equals(modelSize)) {
            recursiveShape = model.expectShape(
                    software.amazon.smithy.model.shapes.ShapeId.from("smithy.bench#T5_0"));
        } else {
            recursiveShape = structureShapes.getFirst();
        }

        // Pre-warm a converter by converting all shapes
        preWarmedConverter = new SchemaConverter(model);
        for (var shape : structureShapes) {
            preWarmedConverter.getSchema(shape);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void convertAllShapes(Blackhole bh) {
        var converter = new SchemaConverter(model);
        for (var shape : structureShapes) {
            bh.consume(converter.getSchema(shape));
        }
    }

    @Benchmark
    public void convertAllShapesCached(Blackhole bh) {
        for (var shape : structureShapes) {
            bh.consume(preWarmedConverter.getSchema(shape));
        }
    }

    @Benchmark
    public void convertSingleRecursiveShape(Blackhole bh) {
        var converter = new SchemaConverter(model);
        bh.consume(converter.getSchema(recursiveShape));
    }

    private static String buildModel(String size) {
        var sb = new StringBuilder();
        sb.append("$version: \"2\"\n");
        sb.append("namespace smithy.bench\n\n");

        switch (size) {
            // 50 flat structures with 5 primitives each, no refs — baseline for overhead measurement
            case "small" -> buildTieredModel(sb,
                    new Tier[] {
                            new Tier(50, 5, List.of(), List.of())
                    },
                    false);
            // 180 structures across 3 tiers: leaves → mid (3 leaf refs) → top (1 leaf + 2 mid refs).
            // Tests basic nesting without traits or path explosion.
            case "medium" -> buildTieredModel(sb,
                    new Tier[] {
                            new Tier(100, 6, List.of(), List.of()),
                            new Tier(50, 3, List.of(3), List.of()),
                            new Tier(30, 2, List.of(1, 2), List.of())
                    },
                    false);
            // 365 structures across 6 tiers with hub topology, forward DAG refs, and traits.
            // T0: leaves, T1: 5 leaf refs + forward DAG within tier (exponential path re-exploration),
            // T2: 5 hub structs with 9 T1 refs each (high fan-out amplifiers),
            // T3-T5: progressive aggregation with cross-tier refs.
            // Mimics large real-world service models where isRecursive() must exhaustively
            // traverse deep, fan-out-heavy subgraphs.
            case "large" -> buildTieredModel(sb,
                    new Tier[] {
                            new Tier(100, 10, List.of(), List.of()),
                            new Tier(80, 6, List.of(5), List.of(9, 18, 27)),
                            new Tier(5, 4, List.of(0, 9), List.of()),
                            new Tier(100, 5, List.of(0, 2, 4), List.of()),
                            new Tier(50, 6, List.of(0, 0, 1, 4), List.of()),
                            new Tier(30, 6, List.of(0, 0, 0, 1, 4), List.of())
                    },
                    true);
            default -> throw new IllegalArgumentException("Unknown model size: " + size);
        }

        return sb.toString();
    }

    private static void buildTieredModel(StringBuilder sb, Tier[] tiers, boolean withTraits) {
        int mIdx = 0;
        for (int t = 0; t < tiers.length; t++) {
            var tier = tiers[t];
            for (int i = 0; i < tier.count; i++) {
                sb.append("structure T").append(t).append('_').append(i).append(" {\n");
                int refIdx = 0;
                // Refs to lower tiers
                for (int lt = 0; lt < tier.refsPerTier.size(); lt++) {
                    for (int r = 0; r < tier.refsPerTier.get(lt); r++) {
                        int target = (i * 37 + refIdx * 13) % tiers[lt].count;
                        mIdx = appendMember(sb, mIdx, "r" + refIdx, "T" + lt + "_" + target, withTraits);
                        refIdx++;
                    }
                }
                // Forward refs within same tier (DAG — higher indices only)
                for (int step : tier.fwdSteps) {
                    if (i + step < tier.count) {
                        mIdx = appendMember(sb, mIdx, "r" + refIdx, "T" + t + "_" + (i + step), withTraits);
                        refIdx++;
                    }
                }
                // Primitive members
                for (int p = 0; p < tier.primCount; p++) {
                    mIdx = appendMember(sb, mIdx, "p" + p, PRIM_TYPES[p % PRIM_TYPES.length], withTraits);
                }
                sb.append("}\n\n");
            }
        }
    }

    private static int appendMember(
            StringBuilder sb,
            int memberIndex,
            String name,
            String type,
            boolean withTraits
    ) {
        if (withTraits) {
            if (memberIndex % 5 < 3) {
                sb.append("    @jsonName(\"").append(name).append("_json\")\n");
            }
            if (memberIndex % 14 == 0) {
                sb.append("    @required\n");
            }
            if (memberIndex % 17 == 0) {
                sb.append("    @documentation(\"Documentation for member ").append(name).append("\")\n");
            }
        }
        sb.append("    ").append(name).append(": ").append(type).append("\n");
        return memberIndex + 1;
    }
}
