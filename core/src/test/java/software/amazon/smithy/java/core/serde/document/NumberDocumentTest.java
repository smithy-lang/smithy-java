/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class NumberDocumentTest {
    @ParameterizedTest
    @MethodSource("defaultSerializationProvider")
    public void defaultSerialization(Number value, ShapeType type) {
        var document = Document.ofNumber(value);

        assertThat(document.type(), equalTo(type));
        assertThat(document.asNumber(), is(value));
        assertThat(document, equalTo(Document.ofNumber(document.asNumber())));

        ShapeSerializer serializer = new InterceptingSerializer() {
            @Override
            protected ShapeSerializer before(Schema schema) {
                assertThat(schema.type(), is(type));
                return ShapeSerializer.nullSerializer();
            }
        };

        document.serializeContents(serializer);
    }

    public static List<Arguments> defaultSerializationProvider() {
        return List.of(
                Arguments.of((byte) 1, ShapeType.BYTE),
                Arguments.of((short) 1, ShapeType.SHORT),
                Arguments.of(1, ShapeType.INTEGER),
                Arguments.of(1L, ShapeType.LONG),
                Arguments.of(1F, ShapeType.FLOAT),
                Arguments.of(1D, ShapeType.DOUBLE),
                Arguments.of(BigInteger.ONE, ShapeType.BIG_INTEGER),
                Arguments.of(BigDecimal.ONE, ShapeType.BIG_DECIMAL));
    }

    @Test
    public void bigDecimalValueConvertsToBigIntegerSchema() {
        var captured = new BigInteger[1];
        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeBigInteger(Schema schema, BigInteger value) {
                captured[0] = value;
            }
        };
        new Documents.NumberDocument(PreludeSchemas.BIG_INTEGER, new BigDecimal("1.5E+3"))
                .serializeContents(serializer);
        assertThat(captured[0], equalTo(BigInteger.valueOf(1500)));
    }

    @Test
    @Timeout(10)
    public void bigDecimalWithExtremeScaleIsRejectedBeforeConversion() {
        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeBigInteger(Schema schema, BigInteger value) {
                throw new AssertionError("conversion should have been rejected");
            }
        };
        var document = new Documents.NumberDocument(PreludeSchemas.BIG_INTEGER, new BigDecimal("1812031E+900000000"));
        assertThrows(SerializationException.class, () -> document.serializeContents(serializer));
    }
}
