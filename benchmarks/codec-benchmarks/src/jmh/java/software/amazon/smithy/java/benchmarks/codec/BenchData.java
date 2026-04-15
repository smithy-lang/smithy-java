/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.codec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.benchmarks.codec.model.BenchUnion;
import software.amazon.smithy.java.benchmarks.codec.model.Color;
import software.amazon.smithy.java.benchmarks.codec.model.ComplexStruct;
import software.amazon.smithy.java.benchmarks.codec.model.InnerStruct;
import software.amazon.smithy.java.benchmarks.codec.model.NestedStruct;
import software.amazon.smithy.java.benchmarks.codec.model.SimpleStruct;
import software.amazon.smithy.java.core.serde.document.Document;

public final class BenchData {

    private BenchData() {}

    public static SimpleStruct buildSimpleStruct() {
        return SimpleStruct.builder()
                .name("benchmark-test")
                .age(42)
                .active(true)
                .score(98.6)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .build();
    }

    public static ComplexStruct buildComplexStruct() {
        var inner = InnerStruct.builder()
                .value("inner-value")
                .numbers(List.of(1, 2, 3, 4, 5))
                .build();
        var nested = NestedStruct.builder()
                .field1("nested-field")
                .field2(100)
                .inner(inner)
                .build();
        var sparseMap = new HashMap<String, String>();
        sparseMap.put("x", "1");
        sparseMap.put("y", "2");
        sparseMap.put("z", null);
        return ComplexStruct.builder()
                .id("bench-001")
                .count(999)
                .enabled(true)
                .ratio(1.618)
                .score(2.718f)
                .bigCount(1_000_000L)
                .optionalString("optional-value")
                .optionalInt(42)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .updatedAt(Instant.parse("2025-06-01T12:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T00:00:00Z"))
                .payload(ByteBuffer.wrap("binary-payload-data".getBytes(StandardCharsets.UTF_8)))
                .tags(List.of("alpha", "beta", "gamma", "delta"))
                .intList(List.of(10, 20, 30, 40, 50))
                .metadata(Map.of("key1", "value1", "key2", "value2", "key3", "value3"))
                .intMap(Map.of("a", 1, "b", 2, "c", 3))
                .nested(nested)
                .optionalNested(NestedStruct.builder()
                        .field1("opt-nested")
                        .field2(200)
                        .build())
                .structList(List.of(nested, nested))
                .structMap(Map.of("first", nested, "second", nested))
                .choice(new BenchUnion.StringValueMember("union-string"))
                .color(Color.GREEN)
                .colorList(List.of(Color.RED, Color.BLUE, Color.YELLOW))
                .sparseStrings(Arrays.asList("a", null, "c"))
                .sparseMap(sparseMap)
                .bigIntValue(new BigInteger("123456789012345678901234567890"))
                .bigDecValue(new BigDecimal("99999.99999"))
                .freeformData(Document.of(Map.of("key", Document.of("value"), "num", Document.of(42))))
                .build();
    }
}
