/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class LongDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.createLong(10L);

        assertThat(document.type(), equalTo(ShapeType.LONG));
        assertThat(document.asLong(), equalTo(10L));
        assertThat(document, equalTo(Document.createLong(10L)));
    }

    @Test
    public void serializesShape() {
        var document = Document.createLong(1L);

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var document = Document.createLong(10L);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeLong(Schema schema, long value) {
                assertThat(schema, equalTo(PreludeSchemas.LONG));
                assertThat(value, equalTo(10L));
            }
        };

        document.serializeContents(serializer);
    }

    @Test
    public void detectsOverflow() {
        Assertions.assertThrows(ArithmeticException.class, () -> Document.createLong(Long.MAX_VALUE).asInteger());
    }
}
