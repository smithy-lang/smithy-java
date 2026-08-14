/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

@State(Scope.Benchmark)
public class JsonTimestampDeserializeBenchmark {

    private static final int COUNT = 32;

    private static final Schema TIMESTAMP = Schema.createTimestamp(
            ShapeId.from("smithy.benchmark#EpochSeconds"),
            new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS));

    @Param({
            "epochSeconds_TenDigit",
            "epochSeconds_NineDigit",
            "epochSeconds_Fractional",
    })
    public String testCaseId;

    private JsonCodec codec;
    private byte[] payload;

    @Setup
    public void setup() {
        codec = JsonCodec.builder()
                .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                .useTimestampFormat(true)
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
                case "epochSeconds_TenDigit" -> sb.append(1_712_345_678L + i);
                case "epochSeconds_NineDigit" -> sb.append(100_000_000L + i);
                case "epochSeconds_Fractional" -> sb.append(1_712_345_678L + i).append(".25");
                default -> throw new IllegalArgumentException("Unknown test case: " + testCaseId);
            }
        }
        return sb.append(']').toString().getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark
    public void deserialize(Blackhole bh) {
        var de = codec.createDeserializer(payload);
        de.readList(PreludeSchemas.DOCUMENT, bh, (sink, listDe) -> {
            Instant value = listDe.readTimestamp(TIMESTAMP);
            sink.consume(value);
        });
    }
}
