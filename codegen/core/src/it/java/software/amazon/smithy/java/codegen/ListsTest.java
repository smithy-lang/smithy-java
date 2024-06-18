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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smithy.codegen.test.model.ListAllTypesInput;
import io.smithy.codegen.test.model.NestedEnum;
import io.smithy.codegen.test.model.NestedIntEnum;
import io.smithy.codegen.test.model.NestedListsInput;
import io.smithy.codegen.test.model.NestedStruct;
import io.smithy.codegen.test.model.NestedUnion;
import io.smithy.codegen.test.model.SetsAllTypesInput;
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
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.json.JsonCodec;

public class ListsTest {

    static Stream<SerializableShape> listTypes() {
        return Stream.of(
            ListAllTypesInput.builder()
                .listOfBoolean(
                    List.of(true, true, false)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfBigDecimal(
                    List.of(BigDecimal.TEN, BigDecimal.ZERO)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfBigInteger(
                    List.of(BigInteger.TEN, BigInteger.ZERO)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfByte(
                    List.of((byte) 1, (byte) 2)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfDouble(
                    List.of(2.0, 3.0)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfFloat(
                    List.of(2f, 3f)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfInteger(
                    List.of(1, 2)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfLong(
                    List.of(1L, 2L)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfShort(
                    List.of((short) 1, (short) 2)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfString(
                    List.of("a", "b")
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfBlobs(
                    List.of(Base64.getDecoder().decode("YmxvYg=="), Base64.getDecoder().decode("YmlyZHM="))
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfTimestamps(
                    List.of(Instant.EPOCH, Instant.MIN)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfUnion(
                    List.of(new NestedUnion.AMember("string"), new NestedUnion.BMember(2))
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfEnum(
                    List.of(NestedEnum.A, NestedEnum.B)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfIntEnum(
                    List.of(NestedIntEnum.A, NestedIntEnum.B)
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfStruct(
                    List.of(NestedStruct.builder().build(), NestedStruct.builder().build())
                )
                .build(),
            ListAllTypesInput.builder()
                .listOfDocuments(
                    List.of(Document.createDouble(2.0), Document.createString("string"))
                )
                .build()
        );
    }

    static Stream<SerializableShape> nestedLists() {
        return Stream.of(
            NestedListsInput.builder()
                .listOfLists(
                    List.of(List.of("a", "b"), List.of("c", "d"))
                )
                .build(),
            NestedListsInput.builder()
                .listOfListOfList(
                    List.of(List.of(List.of("a", "b"), List.of("c", "d")))
                )
                .build(),
            NestedListsInput.builder()
                .listOfMaps(
                    List.of(Map.of("a", "b"), Map.of("c", "d"))
                )
                .build()
        );
    }

    @ParameterizedTest
    @MethodSource({"listTypes", "nestedLists"})
    void pojoToDocumentRoundTrip(SerializableStruct pojo) {
        var output = Utils.pojoToDocumentRoundTrip(pojo);
        assertEquals(pojo.hashCode(), output.hashCode());
        assertEquals(pojo, output);
    }

    @Test
    void nullDistinctFromEmpty() {
        var emptyInput = ListAllTypesInput.builder().listOfBoolean(List.of()).build();
        var nullInput = ListAllTypesInput.builder().build();
        assertNotEquals(emptyInput, nullInput);
        assertTrue(emptyInput.hasListOfBoolean());
        assertFalse(nullInput.hasListOfBoolean());
        // Collections should return empty collections for access
        assertEquals(emptyInput.listOfBoolean(), Collections.emptyList());
        assertEquals(emptyInput.listOfBoolean(), nullInput.listOfBoolean());
        var emptyDocument = Document.createTyped(emptyInput);
        var nullDocument = Document.createTyped(nullInput);
        assertNotNull(emptyDocument.getMember("listOfBoolean"));
        assertNull(nullDocument.getMember("listOfBoolean"));
    }

    // TODO: Fix
    static Stream<String> nonUniqueSources() {
        return Stream.of(
            "{\"setOfBoolean\":[true, false, false]}",
            "{\"setOfNumber\":[1,2,2]}",
            "{\"setOfString\":[\"a\", \"a\", \"b\"]}",
            // TODO: Re-add once fixed "{\"setOfBlobs\":[\"YmxvYg==\", \"YmxvYg==\"]}",
            "{\"setOfTimestamps\":[0, 20, 0]}",
            "{\"setOfUnion\":[{\"a\": \"str\"}, {\"b\": 1}, {\"a\": \"str\"}]}",
            "{\"setOfEnum\":[\"A\", \"B\", \"A\"]}",
            "{\"setOfIntEnum\":[1,1,2]}",
            "{\"setOfStruct\":[{\"fieldA\": \"a\"}, {\"fieldA\": \"a\"}, {\"fieldA\": \"z\"}]",
            "{\"setOfStringList\":[[\"a\", \"b\"],[\"c\", \"d\"],[\"a\", \"b\"]]}",
            "{\"setOfStringMap\": [{\"a\": \"b\", \"c\": \"d\"}, {\"c\": \"d\", \"a\": \"b\"}]"
        );
    }

    @ParameterizedTest
    @MethodSource("nonUniqueSources")
    void nonUniqueThrows(String source) {
        try (var codec = JsonCodec.builder().useJsonName(true).build()) {
            var exc = assertThrows(
                SerializationException.class,
                () -> codec.deserializeShape(source, SetsAllTypesInput.builder())
            );
            assertTrue(exc.getMessage().contains("Member must have unique values"));
        }
    }
}
