/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.nio.ByteBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;

@State(Scope.Thread)
public class QueryFormSerializerBenchmark {

    @Param({
            "ascii_128",
            "unicode_first_128",
            "unicode_last_128",
            "cjk_128",
            "ascii_8192",
            "unicode_last_8192",
    })
    public String testCaseId;

    private Schema member;
    private String value;

    @Setup
    public void setup() {
        int separator = testCaseId.lastIndexOf('_');
        int length = Integer.parseInt(testCaseId.substring(separator + 1));
        value = switch (testCaseId.substring(0, separator)) {
            case "ascii" -> "a".repeat(length);
            case "unicode_first" -> "日" + "a".repeat(length - 1);
            case "unicode_last" -> "a".repeat(length - 1) + "日";
            case "cjk" -> "日".repeat(length);
            default -> throw new IllegalArgumentException("Unknown test case: " + testCaseId);
        };

        Schema struct = Schema.structureBuilder(ShapeId.from("smithy.benchmark#Input"))
                .putMember("value", PreludeSchemas.STRING)
                .build();
        member = struct.member("value");
    }

    @Benchmark
    public ByteBuffer serializeString() {
        QueryFormSerializer serializer = QueryFormSerializer.acquire(
                QueryFormSerializer.QueryVariant.AWS_QUERY,
                "Benchmark",
                "2020-01-01");
        serializer.writeString(member, value);
        return serializer.finish();
    }
}
