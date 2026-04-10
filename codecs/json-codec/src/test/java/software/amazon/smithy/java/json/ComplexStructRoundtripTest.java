/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.bench.model.BenchUnion;
import software.amazon.smithy.java.json.bench.model.Color;
import software.amazon.smithy.java.json.bench.model.ComplexStruct;
import software.amazon.smithy.java.json.bench.model.InnerStruct;
import software.amazon.smithy.java.json.bench.model.NestedStruct;
import software.amazon.smithy.java.json.bench.model.SimpleStruct;
// Providers from ProviderTestBase.JACKSON and ProviderTestBase.SMITHY

/**
 * Tests roundtrip serialization/deserialization of ComplexStruct with both providers.
 * Also tests cross-provider compatibility (serialize with one, deserialize with the other).
 */
public class ComplexStructRoundtripTest extends ProviderTestBase {

    static List<Arguments> namedProviders() {
        return List.of(
                Arguments.of("jackson", JACKSON),
                Arguments.of("smithy", SMITHY));
    }

    static List<Arguments> crossProviders() {
        return List.of(
                Arguments.of("jackson->jackson", JACKSON, JACKSON),
                Arguments.of("smithy->smithy", SMITHY, SMITHY),
                Arguments.of("jackson->smithy", JACKSON, SMITHY),
                Arguments.of("smithy->jackson", SMITHY, JACKSON));
    }

    private static ComplexStruct buildComplexStruct() {
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("crossProviders")
    public void complexStructRoundtrip(String name, JsonSerdeProvider serializer, JsonSerdeProvider deserializer) {
        var serCodec = JsonCodec.builder()
                .overrideSerdeProvider(serializer)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build();
        var deCodec = JsonCodec.builder()
                .overrideSerdeProvider(deserializer)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build();

        var original = buildComplexStruct();
        ByteBuffer serialized = serCodec.serialize(original);
        byte[] bytes = new byte[serialized.remaining()];
        serialized.get(bytes);

        ComplexStruct result = deCodec.deserializeShape(bytes, ComplexStruct.builder());
        assertThat(result.getId()).isEqualTo(original.getId());
        assertThat(result.getCount()).isEqualTo(original.getCount());
        assertThat(result.isEnabled()).isEqualTo(original.isEnabled());
        assertThat(result.getBigCount()).isEqualTo(original.getBigCount());
        assertThat(result.getTags()).isEqualTo(original.getTags());
        assertThat(result.getColor()).isEqualTo(original.getColor());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("namedProviders")
    public void simpleStructRoundtrip(String name, JsonSerdeProvider provider) {
        var codec = JsonCodec.builder()
                .overrideSerdeProvider(provider)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build();

        var original = SimpleStruct.builder()
                .name("test")
                .age(42)
                .active(true)
                .score(98.6)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .build();

        ByteBuffer serialized = codec.serialize(original);
        byte[] bytes = new byte[serialized.remaining()];
        serialized.get(bytes);

        SimpleStruct result = codec.deserializeShape(bytes, SimpleStruct.builder());
        assertThat(result.getName()).isEqualTo(original.getName());
        assertThat(result.getAge()).isEqualTo(original.getAge());
        assertThat(result.isActive()).isEqualTo(original.isActive());
    }
}
