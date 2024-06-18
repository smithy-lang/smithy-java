/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smithy.codegen.test.model.MapAllTypesInput;
import io.smithy.codegen.test.model.NestedEnum;
import io.smithy.codegen.test.model.NestedIntEnum;
import io.smithy.codegen.test.model.NestedMapsInput;
import io.smithy.codegen.test.model.NestedStruct;
import io.smithy.codegen.test.model.NestedUnion;
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
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

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
                    Map.of("a", Base64.getDecoder().decode("YmxvYg=="), "b", Base64.getDecoder().decode("YmlyZHM="))
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

    @ParameterizedTest
    @MethodSource({"mapTypes", "nestedMaps"})
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
