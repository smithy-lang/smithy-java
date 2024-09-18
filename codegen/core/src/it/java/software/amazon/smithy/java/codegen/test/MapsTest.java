/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static java.nio.ByteBuffer.wrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codegen.test.model.MapAllTypesInput;
import software.amazon.smithy.java.codegen.test.model.NestedEnum;
import software.amazon.smithy.java.codegen.test.model.NestedIntEnum;
import software.amazon.smithy.java.codegen.test.model.NestedMapsInput;
import software.amazon.smithy.java.codegen.test.model.NestedStruct;
import software.amazon.smithy.java.codegen.test.model.NestedUnion;
import software.amazon.smithy.java.codegen.test.model.SparseMapsInput;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.utils.MapUtils;

public class MapsTest {

    static Stream<SerializableShape> mapTypes() {
        return Stream.of(
            MapAllTypesInput.builder()
                .stringBooleanMap(
                    Map.of("a", true, "b", false)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringBigDecimalMap(
                    Map.of("one", BigDecimal.ONE, "ten", BigDecimal.TEN)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringBigIntegerMap(
                    Map.of("one", BigInteger.ONE, "ten", BigInteger.TEN)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringByteMap(
                    Map.of("one", (byte) 1, "two", (byte) 2)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringDoubleMap(
                    Map.of("one", 1.0, "two", 2.0)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringFloatMap(
                    Map.of("one", 1f, "two", 2f)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringIntegerMap(
                    Map.of("one", 1, "two", 2)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringLongMap(
                    Map.of("one", 1L, "two", 2L)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringShortMap(
                    Map.of("one", (short) 1, "two", (short) 2)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringStringMap(
                    Map.of("a", "b", "c", "d")
                )
                .build(),
            MapAllTypesInput.builder()
                .stringBlobMap(
                    Map.of(
                        "a",
                        wrap(Base64.getDecoder().decode("YmxvYg==")),
                        "b",
                        wrap(Base64.getDecoder().decode("YmlyZHM="))
                    )
                )
                .build(),
            MapAllTypesInput.builder()
                .stringTimestampMap(
                    Map.of("epoch", Instant.EPOCH, "min", Instant.MIN)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringUnionMap(
                    Map.of("a", new NestedUnion.AMember("a"), "b", new NestedUnion.BMember(1))
                )
                .build(),
            MapAllTypesInput.builder()
                .stringEnumMap(
                    Map.of("a", NestedEnum.A, "b", NestedEnum.B)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringIntEnumMap(
                    Map.of("a", NestedIntEnum.A, "b", NestedIntEnum.B)
                )
                .build(),
            MapAllTypesInput.builder()
                .stringStructMap(
                    Map.of("a", NestedStruct.builder().build(), "b", NestedStruct.builder().fieldA("a").build())
                )
                .build()
        );
    }

    static Stream<SerializableShape> nestedMaps() {
        return Stream.of(
            NestedMapsInput.builder()
                .mapOfStringMap(
                    Map.of("a", Map.of("b", "c"), "d", Map.of("e", "f"))
                )
                .build(),
            NestedMapsInput.builder()
                .mapOfMapOfStringMap(
                    Map.of("a", Map.of("b", Map.of("c", "d")))
                )
                .build(),
            NestedMapsInput.builder()
                .mapOfStringList(
                    Map.of("a", List.of("b", "c"))
                )
                .build(),
            NestedMapsInput.builder()
                .mapOfMapList(
                    Map.of("a", List.of(Map.of("b", "c"), Map.of("d", "e")))
                )
                .build()
        );
    }

    static Stream<SerializableShape> sparseMaps() {
        return Stream.of(
            SparseMapsInput.builder()
                .stringBooleanMap(
                    MapUtils.of("a", true, "null", null, "b", false)
                )
                .build(),
            SparseMapsInput.builder()
                .stringBigDecimalMap(
                    MapUtils.of("one", BigDecimal.ONE, "null", null, "ten", BigDecimal.TEN)
                )
                .build(),
            SparseMapsInput.builder()
                .stringBigIntegerMap(
                    MapUtils.of("one", BigInteger.ONE, "null", null, "ten", BigInteger.TEN)
                )
                .build(),
            SparseMapsInput.builder()
                .stringByteMap(
                    MapUtils.of("one", (byte) 1, "null", null, "two", (byte) 2)
                )
                .build(),
            SparseMapsInput.builder()
                .stringDoubleMap(
                    MapUtils.of("one", 1.0, "null", null, "two", 2.0)
                )
                .build(),
            SparseMapsInput.builder()
                .stringFloatMap(
                    MapUtils.of("one", 1f, "null", null, "two", 2f)
                )
                .build(),
            SparseMapsInput.builder()
                .stringIntegerMap(
                    MapUtils.of("one", 1, "null", null, "two", 2)
                )
                .build(),
            SparseMapsInput.builder()
                .stringLongMap(
                    MapUtils.of("one", 1L, "null", null, "two", 2L)
                )
                .build(),
            SparseMapsInput.builder()
                .stringShortMap(
                    MapUtils.of("one", (short) 1, "null", null, "two", (short) 2)
                )
                .build(),
            SparseMapsInput.builder()
                .stringStringMap(
                    MapUtils.of("a", "b", "null", null, "c", "d")
                )
                .build(),
            SparseMapsInput.builder()
                .stringBlobMap(
                    MapUtils.of(
                        "a",
                        wrap(Base64.getDecoder().decode("YmxvYg==")),
                        "null",
                        null,
                        "b",
                        wrap(Base64.getDecoder().decode("YmlyZHM="))
                    )
                )
                .build(),
            SparseMapsInput.builder()
                .stringTimestampMap(
                    MapUtils.of("epoch", Instant.EPOCH, "null", null, "min", Instant.MIN)
                )
                .build(),
            SparseMapsInput.builder()
                .stringUnionMap(
                    MapUtils.of("a", new NestedUnion.AMember("a"), "null", null, "b", new NestedUnion.BMember(1))
                )
                .build(),
            SparseMapsInput.builder()
                .stringEnumMap(
                    MapUtils.of("a", NestedEnum.A, "null", null, "b", NestedEnum.B)
                )
                .build(),
            SparseMapsInput.builder()
                .stringIntEnumMap(
                    MapUtils.of("a", NestedIntEnum.A, "null", null, "b", NestedIntEnum.B)
                )
                .build(),
            SparseMapsInput.builder()
                .stringStructMap(
                    MapUtils.of(
                        "a",
                        NestedStruct.builder().build(),
                        "null",
                        null,
                        "b",
                        NestedStruct.builder().fieldA("a").build()
                    )
                )
                .build()
        );
    }

    @ParameterizedTest
    @MethodSource({"mapTypes", "nestedMaps", "sparseMaps"})
    void pojoToDocumentRoundTrip(SerializableStruct pojo) {
        var output = Utils.pojoToDocumentRoundTrip(pojo);
        assertEquals(pojo.hashCode(), output.hashCode());
        assertEquals(pojo, output);
    }

    @Test
    void nullDistinctFromEmpty() {
        var emptyInput = MapAllTypesInput.builder().stringBooleanMap(Map.of()).build();
        var nullInput = MapAllTypesInput.builder().build();
        assertNotEquals(emptyInput, nullInput);
        assertTrue(emptyInput.hasStringBooleanMap());
        assertFalse(nullInput.hasStringBooleanMap());
        // Collections should return empty collections for access
        assertEquals(emptyInput.stringBooleanMap(), Collections.emptyMap());
        assertEquals(emptyInput.stringBooleanMap(), nullInput.stringBooleanMap());

        var emptyDocument = Document.createTyped(emptyInput);
        var nullDocument = Document.createTyped(nullInput);
        assertNotNull(emptyDocument.getMember("stringBooleanMap"));
        assertNull(nullDocument.getMember("stringBooleanMap"));
    }
}
