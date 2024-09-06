/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
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
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var document = Document.createDouble(1.0);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeDouble(Schema schema, double value) {
                assertThat(schema, equalTo(PreludeSchemas.DOUBLE));
                assertThat(value, equalTo(1.0));
            }
        };

        document.serializeContents(serializer);
    }

    @Test
    public void canCast() {
        assertThat(Document.createDouble(1.1111).asInteger(), is(1));
    }
}
