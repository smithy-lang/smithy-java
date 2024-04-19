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

public class FloatDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.of(1.0f);

        assertThat(document.type(), equalTo(ShapeType.FLOAT));
        assertThat(document.asFloat(), equalTo(1.0f));
        assertThat(document, equalTo(Document.of(1.0f)));
    }

    @Test
    public void serializesShape() {
        var document = Document.of(1.0f);

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var document = Document.of(1.0f);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeFloat(Schema schema, float value) {
                assertThat(schema, equalTo(PreludeSchemas.FLOAT));
                assertThat(value, equalTo(1.0f));
            }
        };

        document.serializeContents(serializer);
    }

    @Test
    public void canWiden() {
        var document = Document.of(1.0f);

        assertThat(document.asDouble(), equalTo(1.0));
    }
}
