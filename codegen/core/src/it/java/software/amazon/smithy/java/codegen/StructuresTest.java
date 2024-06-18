/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.smithy.codegen.test.model.BigDecimalMembersInput;
import io.smithy.codegen.test.model.BigIntegerMembersInput;
import io.smithy.codegen.test.model.BlobMembersInput;
import io.smithy.codegen.test.model.BooleanMembersInput;
import io.smithy.codegen.test.model.ByteMembersInput;
import io.smithy.codegen.test.model.DocumentMembersInput;
import io.smithy.codegen.test.model.DoubleMembersInput;
import io.smithy.codegen.test.model.EnumMembersInput;
import io.smithy.codegen.test.model.EnumType;
import io.smithy.codegen.test.model.FloatMembersInput;
import io.smithy.codegen.test.model.IntEnumMembersInput;
import io.smithy.codegen.test.model.IntEnumType;
import io.smithy.codegen.test.model.IntegerMembersInput;
import io.smithy.codegen.test.model.ListMembersInput;
import io.smithy.codegen.test.model.LongMembersInput;
import io.smithy.codegen.test.model.MapMembersInput;
import io.smithy.codegen.test.model.NestedStruct;
import io.smithy.codegen.test.model.NestedUnion;
import io.smithy.codegen.test.model.ShortMembersInput;
import io.smithy.codegen.test.model.StringMembersInput;
import io.smithy.codegen.test.model.StructureMembersInput;
import io.smithy.codegen.test.model.TimestampMembersInput;
import io.smithy.codegen.test.model.UnionMembersInput;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
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
        var builder = BlobMembersInput.builder().requiredBlob("data".getBytes());
        builder.setDataStream(datastream);
        var input = builder.build();
        var document = Document.createTyped(builder.build());
        var outputBuilder = BlobMembersInput.builder();
        document.deserializeInto(outputBuilder);
        outputBuilder.setDataStream(input.streamingBlob());
        var output = builder.build();
        assertEquals(input.hashCode(), output.hashCode());
        assertEquals(input, output);
    }
}
