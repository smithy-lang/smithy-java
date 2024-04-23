/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class BigIntegerDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.createBigInteger(BigInteger.valueOf(10));

        assertThat(document.type(), equalTo(ShapeType.BIG_INTEGER));
        assertThat(document.asBigInteger(), equalTo(BigInteger.valueOf(10)));
        assertThat(document, equalTo(Document.createBigInteger(BigInteger.valueOf(10))));
    }

    @Test
    public void serializesShape() {
        var document = Document.createBigInteger(BigInteger.valueOf(10));

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(SdkSchema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var document = Document.createBigInteger(BigInteger.valueOf(10));

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeBigInteger(SdkSchema schema, BigInteger value) {
                assertThat(schema, equalTo(PreludeSchemas.BIG_INTEGER));
                assertThat(value, equalTo(BigInteger.valueOf(10)));
            }
        };

        document.serializeContents(serializer);
    }
}
