/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

@State(Scope.Benchmark)
public class JsonNumberDeserializeBenchmark {

    private static final int COUNT = 32;

    @Param({
            "numbers_WholeSmall",
            "numbers_WholeLong",
            "numbers_Fractional",
            "numbers_Exponent",
            "numbers_OverBudget",
    })
    public String testCaseId;

    private JsonCodec codec;
    private byte[] payload;

    @Setup
    public void setup() {
        codec = JsonCodec.builder()
                .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                .build();
        payload = buildPayload(testCaseId);
    }

    private static byte[] buildPayload(String testCaseId) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < COUNT; i++) {
            if (i > 0) {
                sb.append(',');
            }
            switch (testCaseId) {
                case "numbers_WholeSmall" -> sb.append(1000 + i);
                case "numbers_WholeLong" -> sb.append(123_456_789_012_345_000L + i);
                case "numbers_Fractional" -> sb.append(1000 + i).append(".5");
                case "numbers_Exponent" -> sb.append(1000 + i).append(".25e7");
                case "numbers_OverBudget" -> sb.append(1_234_567_890_123_456_789L + i);
                default -> throw new IllegalArgumentException("Unknown test case: " + testCaseId);
            }
        }
        return sb.append(']').toString().getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark
    public void deserializeDouble(Blackhole bh) {
        var de = codec.createDeserializer(payload);
        de.readList(PreludeSchemas.DOCUMENT, bh, (sink, listDe) -> {
            sink.consume(listDe.readDouble(PreludeSchemas.DOUBLE));
        });
    }

    @Benchmark
    public void deserializeDocument(Blackhole bh) {
        bh.consume(codec.createDeserializer(payload).readDocument());
    }
}
