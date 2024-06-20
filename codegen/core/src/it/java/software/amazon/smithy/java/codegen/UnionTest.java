/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.smithy.codegen.test.model.NestedEnum;
import io.smithy.codegen.test.model.NestedIntEnum;
import io.smithy.codegen.test.model.NestedStruct;
import io.smithy.codegen.test.model.NestedUnion;
import io.smithy.codegen.test.model.UnionType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

public class UnionTest {

    static Stream<SerializableShape> unionTypes() {
        return Stream.of(
            new UnionType.BooleanValueMember(true),
            new UnionType.ListValueMember(List.of("a", "b")),
            new UnionType.MapValueMember(Map.of("a", "b")),
            new UnionType.BigDecimalValueMember(BigDecimal.ONE),
            new UnionType.BigIntegerValueMember(BigInteger.ONE),
            new UnionType.ByteValueMember((byte) 1),
            new UnionType.DoubleValueMember(2.0),
            new UnionType.FloatValueMember(2f),
            new UnionType.IntegerValueMember(1),
            new UnionType.LongValueMember(1L),
            new UnionType.ShortValueMember((short) 1),
            new UnionType.StringValueMember("string"),
            new UnionType.BlobValueMember(Base64.getDecoder().decode("c3RyZWFtaW5n")),
            new UnionType.StructureValueMember(NestedStruct.builder().build()),
            new UnionType.TimestampValueMember(Instant.EPOCH),
            new UnionType.UnionValueMember(new NestedUnion.BMember(1)),
            new UnionType.EnumValueMember(NestedEnum.A),
            new UnionType.IntEnumValueMember(NestedIntEnum.A)
        );
    }


    @ParameterizedTest
    @MethodSource("unionTypes")
    void pojoToDocumentRoundTrip(UnionType pojo) {
        var document = Document.createTyped(pojo);
        var builder = UnionType.builder();
        document.deserializeInto(builder);
        var output = builder.build();
        assertEquals(pojo.hashCode(), output.hashCode());
        assertEquals(pojo, output);
    }
}
