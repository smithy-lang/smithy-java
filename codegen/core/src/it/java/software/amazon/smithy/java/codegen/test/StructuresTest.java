/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static java.nio.ByteBuffer.wrap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codegen.test.model.BigDecimalMembersInput;
import software.amazon.smithy.java.codegen.test.model.BigIntegerMembersInput;
import software.amazon.smithy.java.codegen.test.model.BlobMembersInput;
import software.amazon.smithy.java.codegen.test.model.BooleanMembersInput;
import software.amazon.smithy.java.codegen.test.model.ByteMembersInput;
import software.amazon.smithy.java.codegen.test.model.DocumentMembersInput;
import software.amazon.smithy.java.codegen.test.model.DoubleMembersInput;
import software.amazon.smithy.java.codegen.test.model.EnumMembersInput;
import software.amazon.smithy.java.codegen.test.model.EnumType;
import software.amazon.smithy.java.codegen.test.model.FloatMembersInput;
import software.amazon.smithy.java.codegen.test.model.IntEnumMembersInput;
import software.amazon.smithy.java.codegen.test.model.IntEnumType;
import software.amazon.smithy.java.codegen.test.model.IntegerMembersInput;
import software.amazon.smithy.java.codegen.test.model.ListMembersInput;
import software.amazon.smithy.java.codegen.test.model.LongMembersInput;
import software.amazon.smithy.java.codegen.test.model.MapMembersInput;
import software.amazon.smithy.java.codegen.test.model.NestedStruct;
import software.amazon.smithy.java.codegen.test.model.NestedUnion;
import software.amazon.smithy.java.codegen.test.model.ShortMembersInput;
import software.amazon.smithy.java.codegen.test.model.StringMembersInput;
import software.amazon.smithy.java.codegen.test.model.StructureMembersInput;
import software.amazon.smithy.java.codegen.test.model.TimestampMembersInput;
import software.amazon.smithy.java.codegen.test.model.UnionMembersInput;
import software.amazon.smithy.java.runtime.common.datastream.DataStream;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

public class StructuresTest {
    static Stream<SerializableShape> memberTypes() {
        return Stream.of(
            BooleanMembersInput.builder().requiredBoolean(true).build(),
            DocumentMembersInput.builder().requiredDoc(Document.createString("str")).build(),
            ListMembersInput.builder().requiredList(List.of("a", "b", "c")).build(),
            MapMembersInput.builder().requiredMap(Map.of("a", "b")).build(),
            BigDecimalMembersInput.builder().requiredBigDecimal(BigDecimal.valueOf(1.0)).build(),
            BigIntegerMembersInput.builder().requiredBigInteger(BigInteger.valueOf(1L)).build(),
            ByteMembersInput.builder().requiredByte((byte) 1).build(),
            DoubleMembersInput.builder().requiredDouble(2.0).build(),
            FloatMembersInput.builder().requiredFloat(1f).build(),
            IntegerMembersInput.builder().requiredInt(1).build(),
            LongMembersInput.builder().requiredLongs(1L).build(),
            ShortMembersInput.builder().requiredShort((short) 1).build(),
            StringMembersInput.builder().requiredString("required").build(),
            StructureMembersInput.builder()
                .requiredStruct(NestedStruct.builder().fieldA("a").fieldB(2).build())
                .build(),
            TimestampMembersInput.builder().requiredTimestamp(Instant.ofEpochMilli(111111111L)).build(),
            UnionMembersInput.builder().requiredUnion(new NestedUnion.AMember("string")).build(),
            EnumMembersInput.builder().requiredEnum(EnumType.OPTION_ONE).build(),
            IntEnumMembersInput.builder().requiredEnum(IntEnumType.OPTION_ONE).build()
        );
    }

    @ParameterizedTest
    @MethodSource("memberTypes")
    void pojoToDocumentRoundTrip(SerializableStruct pojo) {
        var output = Utils.pojoToDocumentRoundTrip(pojo);
        assertEquals(pojo.hashCode(), output.hashCode());
        assertEquals(pojo, output);
    }

    @Test
    void blobSerialization() {
        var datastream = DataStream.ofBytes("data streeeeeeeeeeam".getBytes());
        var builder = BlobMembersInput
            .builder()
            .requiredBlob(wrap("data".getBytes()))
            .streamingBlob(datastream);

        var input = builder.build();
        var document = Document.createTyped(builder.build());
        var outputBuilder = BlobMembersInput.builder();
        document.deserializeInto(outputBuilder);
        var output = builder.build();

        assertEquals(input.hashCode(), output.hashCode());
        assertEquals(input, output);
    }
}
