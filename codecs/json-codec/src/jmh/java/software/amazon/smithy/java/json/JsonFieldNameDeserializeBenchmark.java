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
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;
import software.amazon.smithy.model.shapes.ShapeId;

@State(Scope.Benchmark)
public class JsonFieldNameDeserializeBenchmark {

    private static final int OBJECT_COUNT = 32;
    private static final int FIELD_COUNT = 8;
    private static final int FIELD_READS = OBJECT_COUNT * FIELD_COUNT;

    @Param({
            "inOrder_OneByte",
            "inOrder_ThreeBytes",
            "inOrder_FourBytes",
            "inOrder_SevenBytes",
            "inOrder_EightBytes",
            "reverseOrder_SevenBytes",
    })
    public String testCaseId;

    private JsonCodec codec;
    private Schema schema;
    private byte[] payload;
    private final Counter counter = new Counter();

    @Setup
    public void setup() {
        codec = JsonCodec.builder()
                .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                .build();
        String[] names = names(testCaseId);
        schema = schema(names);
        payload = payload(names, testCaseId.startsWith("reverseOrder"));
    }

    private static String[] names(String testCaseId) {
        String format = switch (testCaseId) {
            case "inOrder_OneByte" -> "%c";
            case "inOrder_ThreeBytes" -> "f%02d";
            case "inOrder_FourBytes" -> "f%03d";
            case "inOrder_SevenBytes", "reverseOrder_SevenBytes" -> "field%02d";
            case "inOrder_EightBytes" -> "field%03d";
            default -> throw new IllegalArgumentException("Unknown test case: " + testCaseId);
        };

        String[] names = new String[FIELD_COUNT];
        for (int i = 0; i < names.length; i++) {
            names[i] = testCaseId.equals("inOrder_OneByte")
                    ? format.formatted((char) ('a' + i))
                    : format.formatted(i);
        }
        return names;
    }

    private static Schema schema(String[] names) {
        var builder = Schema.structureBuilder(ShapeId.from("smithy.benchmark#Fields"));
        for (String name : names) {
            builder.putMember(name, PreludeSchemas.STRING);
        }
        return builder.build();
    }

    private static byte[] payload(String[] names, boolean reverse) {
        var result = new StringBuilder("[");
        for (int object = 0; object < OBJECT_COUNT; object++) {
            if (object > 0) {
                result.append(',');
            }
            result.append('{');
            for (int field = 0; field < FIELD_COUNT; field++) {
                if (field > 0) {
                    result.append(',');
                }
                int index = reverse ? FIELD_COUNT - field - 1 : field;
                result.append('"').append(names[index]).append("\":\"value\"");
            }
            result.append('}');
        }
        return result.append(']').toString().getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark
    @OperationsPerInvocation(FIELD_READS)
    public int deserialize() {
        counter.total = 0;
        codec.createDeserializer(payload)
                .readList(PreludeSchemas.DOCUMENT, counter, (state, listDeserializer) -> {
                    listDeserializer.readStruct(schema, state, (result, member, structDeserializer) -> {
                        result.total += structDeserializer.readString(member).length();
                    });
                });
        return counter.total;
    }

    private static final class Counter {
        private int total;
    }
}
