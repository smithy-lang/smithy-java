/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smithy.codegen.test.model.BigDecimalsInput;
import io.smithy.codegen.test.model.BigIntegersInput;
import io.smithy.codegen.test.model.BlobsInput;
import io.smithy.codegen.test.model.BooleansInput;
import io.smithy.codegen.test.model.BytesInput;
import io.smithy.codegen.test.model.DoublesInput;
import io.smithy.codegen.test.model.ExceptionWithExtraStringException;
import io.smithy.codegen.test.model.FloatsInput;
import io.smithy.codegen.test.model.IntegersInput;
import io.smithy.codegen.test.model.ListWithStructsInput;
import io.smithy.codegen.test.model.ListsInput;
import io.smithy.codegen.test.model.LongsInput;
import io.smithy.codegen.test.model.MapsInput;
import io.smithy.codegen.test.model.Nested;
import io.smithy.codegen.test.model.NestedListsInput;
import io.smithy.codegen.test.model.NestedMapsInput;
import io.smithy.codegen.test.model.SetsInput;
import io.smithy.codegen.test.model.ShortsInput;
import io.smithy.codegen.test.model.SimpleException;
import io.smithy.codegen.test.model.StringsInput;
import io.smithy.codegen.test.model.StructuresInput;
import io.smithy.codegen.test.model.TimestampsInput;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.json.JsonCodec;

/**
 * Integration tests that serializes and then deserialize a POJO
 */
public class SerdeTest {
    static Stream<SerializableShape> source() {
        return Stream.of(
            // Booleans
            BooleansInput.builder().requiredBoolean(true).build(),
            // Lists
            ListsInput.builder().requiredList(List.of("a", "b", "c")).build(),
            NestedListsInput.builder()
                .listOfLists(List.of(List.of("a", "b"), List.of("c", "d")))
                .listOfListOfList(List.of(List.of(List.of("a", "b"), List.of("c", "d"))))
                .listOfMaps(List.of(Map.of("a", "b"), Map.of("c", "d")))
                .build(),
            ListWithStructsInput.builder()
                .listOfStructs(
                    List.of(
                        Nested.builder().fieldB(1).build(),
                        Nested.builder().fieldA("a").build()
                    )
                )
                .build(),
            // Maps
            MapsInput.builder().requiredMap(Map.of("a", "b")).build(),
            NestedMapsInput.builder()
                .mapOfStringMap(Map.of("a", Map.of("b", "c"), "d", Map.of("e", "f")))
                .mapOfMapOfStringMap(Map.of("a", Map.of("b", Map.of("c", "d"))))
                .mapOfStringList(Map.of("a", List.of("b", "c")))
                .mapOfMapList(Map.of("a", List.of(Map.of("b", "c"), Map.of("d", "e"))))
                .build(),
            // Number
            BigDecimalsInput.builder().requiredBigDecimal(BigDecimal.valueOf(1.0)).build(),
            BigIntegersInput.builder().requiredBigInteger(BigInteger.valueOf(1L)).build(),
            BytesInput.builder().requiredByte((byte) 1).build(),
            DoublesInput.builder().requiredDouble(2.0).build(),
            FloatsInput.builder().requiredFloat(1f).build(),
            IntegersInput.builder().requiredInt(1).build(),
            LongsInput.builder().requiredLongs(1L).build(),
            ShortsInput.builder().requiredShort((short) 1).build(),
            // String
            StringsInput.builder().requiredString("required").build(),
            // Structure
            StructuresInput.builder()
                .requiredStruct(Nested.builder().fieldA("a").fieldB(2).build())
                .build(),
            // Timestamp
            TimestampsInput.builder().requiredTimestamp(Instant.ofEpochMilli(111111111L)).build()
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    <T extends SerializableStruct> void pojoToDocumentRoundTrip(T pojo) {
        var document = Document.createTyped(pojo);
        SdkShapeBuilder<T> builder = getBuilder(pojo);
        document.deserializeInto(builder);
        var output = builder.build();
        assertEquals(pojo.hashCode(), output.hashCode());
        assertEquals(pojo, output);
    }

    static Stream<SerializableShape> exceptions() {
        return Stream.of(
            SimpleException.builder().message("OOOPS!").build(),
            ExceptionWithExtraStringException.builder().message("whoopsy").extra("daisy").build()
        );
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void simpleExceptionToDocument(ModeledSdkException exception) {
        var document = Document.createTyped(exception);
        SdkShapeBuilder<ModeledSdkException> builder = getBuilder(exception);
        document.deserializeInto(builder);
        var output = builder.build();
        assertEquals(exception.getMessage(), output.getMessage());
        assertEquals(exception.getCause(), output.getCause());
        assertNotEquals(exception.hashCode(), output.hashCode());
    }

    @Test
    void blobSerialization() {
        var datastream = DataStream.ofBytes("data streeeeeeeeeeam".getBytes());
        var builder = BlobsInput.builder().requiredBlob("data".getBytes());
        builder.setDataStream(datastream);
        var input = builder.build();
        var document = Document.createTyped(builder.build());
        var outputBuilder = BlobsInput.builder();
        document.deserializeInto(outputBuilder);
        outputBuilder.setDataStream(input.streamingBlob());
        var output = builder.build();
        assertEquals(input.hashCode(), output.hashCode());
        assertEquals(input, output);
    }

    @Test
    void uniqueItemListThrowsSerdeException() {
        try (var codec = JsonCodec.builder().useJsonName(true).build()) {
            var de = codec.createDeserializer(
                "{\"requiredList\":[\"a\",\"b\",\"b\",\"c\"]}".getBytes(StandardCharsets.UTF_8)
            );

            var exc = assertThrows(SdkSerdeException.class, () -> SetsInput.builder().deserialize(de));
            assertTrue(exc.getMessage().contains("b"));
        }
    }

    @SuppressWarnings("unchecked")
    private static <P extends SerializableShape> SdkShapeBuilder<P> getBuilder(P pojo) {
        try {
            var method = pojo.getClass().getDeclaredMethod("builder");
            return (SdkShapeBuilder<P>) method.invoke(pojo);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
