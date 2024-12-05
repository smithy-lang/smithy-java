/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codegen.test.model.NestedEnum;
import software.amazon.smithy.java.codegen.test.model.NestedIntEnum;
import software.amazon.smithy.java.codegen.test.model.NestedStruct;
import software.amazon.smithy.java.codegen.test.model.NestedUnion;
import software.amazon.smithy.java.codegen.test.model.UnionType;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

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
            new UnionType.BlobValueMember(ByteBuffer.wrap(Base64.getDecoder().decode("c3RyZWFtaW5n"))),
            new UnionType.StructureValueMember(NestedStruct.builder().build()),
            new UnionType.TimestampValueMember(Instant.EPOCH),
            new UnionType.UnionValueMember(new NestedUnion.BMember(1)),
            new UnionType.EnumValueMember(NestedEnum.A),
            new UnionType.IntEnumValueMember(NestedIntEnum.A),
            new UnionType.UnitValueMember()
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

    record UnknownDocument() implements Document {

        private static final Map<String, Document> members = Map.of("UNKNOWN!!!", Document.createDouble(3.2));

        @Override
        public ShapeType type() {
            return ShapeType.UNION;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Document getMember(String memberName) {
            return members.get(memberName);
        }

        @Override
        public Set<String> getMemberNames() {
            return members.keySet();
        }

        @Override
        public Map<String, Document> asStringMap() {
            return members;
        }
    }

    @Test
    void unknownUnionDeser() {
        var document = new UnknownDocument();
        var builder = UnionType.builder();
        document.deserializeInto(builder);
        var output = builder.build();

        assertEquals(UnionType.Type.$UNKNOWN, output.type());
        assertEquals("UNKNOWN!!!", output.getValue());
    }

    @Test
    void unknownUnionSerFails() {
        var union = UnionType.builder().$unknownMember("foo").build();
        assertThrows(UnsupportedOperationException.class, () -> Document.createTyped(union));
    }
}
