/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class NumberDocumentTest {
    @ParameterizedTest
    @MethodSource("defaultSerializationProvider")
    public void defaultSerialization(Number value, ShapeType type) {
        var document = Document.createNumber(value);

        assertThat(document.type(), equalTo(type));
        assertThat(document.asNumber(), is(value));
        assertThat(document, equalTo(Document.createNumber(document.asNumber())));

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
            Arguments.of(BigDecimal.ONE, ShapeType.BIG_DECIMAL)
        );
    }
}
