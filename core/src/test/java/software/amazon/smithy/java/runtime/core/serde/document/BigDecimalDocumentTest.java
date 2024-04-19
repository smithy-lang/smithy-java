/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class BigDecimalDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.of(new BigDecimal(10));

        assertThat(document.type(), equalTo(ShapeType.BIG_DECIMAL));
        assertThat(document.asBigDecimal(), equalTo(new BigDecimal(10)));
        assertThat(document, equalTo(Document.of(new BigDecimal(10))));
    }

    @Test
    public void serializesShape() {
        var document = Document.of(new BigDecimal(10));

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var document = Document.of(new BigDecimal(10));

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeBigDecimal(Schema schema, BigDecimal value) {
                assertThat(schema, equalTo(PreludeSchemas.BIG_DECIMAL));
                assertThat(value, equalTo(new BigDecimal(10)));
            }
        };

        document.serializeContents(serializer);
    }
}
