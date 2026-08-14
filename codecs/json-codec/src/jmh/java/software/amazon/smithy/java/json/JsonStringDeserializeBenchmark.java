/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

@State(Scope.Benchmark)
public class JsonStringDeserializeBenchmark {

    private static final int COUNT = 32;

    @Param({
            "ascii_7",
            "ascii_8",
            "ascii_15",
            "ascii_16",
            "ascii_120",
            "nonAsciiShort",
            "nonAsciiLong",
            "escapedShort",
            "escapedLong",
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
        var result = new StringBuilder("[");
        for (int i = 0; i < COUNT; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append('"').append(value(testCaseId, i)).append('"');
        }
        return result.append(']').toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String value(String testCaseId, int i) {
        return switch (testCaseId) {
            case "ascii_7" -> String.format("%07d", i);
            case "ascii_8" -> String.format("k%07d", i);
            case "ascii_15" -> String.format("value-%09d", i);
            case "ascii_16" -> String.format("values-%09d", i);
            case "ascii_120" -> "a".repeat(112) + String.format("%08d", i);
            case "nonAsciiShort" -> String.format("é%02d", i);
            case "nonAsciiLong" -> "中文字符串测试内容值".repeat(2) + i;
            case "escapedShort" -> String.format("\\n%02d", i);
            case "escapedLong" -> "a".repeat(48) + String.format("\\\"%06d", i);
            default -> throw new IllegalArgumentException("Unknown test case: " + testCaseId);
        };
    }

    @Benchmark
    @OperationsPerInvocation(COUNT)
    public void deserialize(Blackhole bh) {
        var deserializer = codec.createDeserializer(payload);
        deserializer.readList(PreludeSchemas.DOCUMENT, bh, (sink, listDeserializer) -> {
            sink.consume(listDeserializer.readString(PreludeSchemas.STRING));
        });
    }
}
