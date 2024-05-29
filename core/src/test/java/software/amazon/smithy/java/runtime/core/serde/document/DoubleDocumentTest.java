/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class DoubleDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.createDouble(1.0);

        assertThat(document.type(), equalTo(ShapeType.DOUBLE));
        assertThat(document.asDouble(), equalTo(1.0));
        assertThat(document, equalTo(Document.createDouble(1.0)));
    }

    @Test
    public void serializesShape() {
        var document = Document.createDouble(1.0);

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(SdkSchema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var document = Document.createDouble(1.0);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeDouble(SdkSchema schema, double value) {
                assertThat(schema, equalTo(PreludeSchemas.DOUBLE));
                assertThat(value, equalTo(1.0));
            }
        };

        document.serializeContents(serializer);
    }

    @ParameterizedTest
    @MethodSource("floatProvider")
    public void floatConversion(double value, boolean isValid) {
        try {
            var document = Document.createDouble(value);
            document.asFloat();
            if (!isValid) {
                Assertions.fail("Expected " + value + " conversion to fail");
            }
        } catch (ArithmeticException e) {
            if (isValid) {
                throw e;
            }
        }
    }

    static List<Arguments> floatProvider() {
        return List.of(
            Arguments.of(Double.MAX_VALUE, false),
            Arguments.of(1.0d, true),
            Arguments.of(-1.0d, true),
            Arguments.of(-10000d, true),
            Arguments.of(-10000, true),
            Arguments.of(Float.MAX_VALUE, true),
            Arguments.of(Float.MIN_VALUE, true),
            Arguments.of(Double.POSITIVE_INFINITY, true),
            Arguments.of(Double.NEGATIVE_INFINITY, true),
            Arguments.of(Double.NaN, true)
        );
    }

    @Test
    public void convertsInfinite() {
        assertThat(Document.createDouble(Double.NaN).asFloat(), equalTo(Float.NaN));
        assertThat(Document.createDouble(Double.NEGATIVE_INFINITY).asFloat(), equalTo(Float.NEGATIVE_INFINITY));
        assertThat(Document.createDouble(Double.POSITIVE_INFINITY).asFloat(), equalTo(Float.POSITIVE_INFINITY));
    }

    @Test
    public void lossyConversionsAreFine() {
        assertThat(Document.createDouble(1.1111).asInteger(), is(1));
    }
}
