/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class BooleanDocumentTest {
    @Test
    public void createsDocument() {
        var document = Document.createBoolean(true);

        assertThat(document.type(), equalTo(ShapeType.BOOLEAN));
        assertThat(document.asBoolean(), equalTo(true));
        assertThat(document, equalTo(Document.createBoolean(true)));
    }

    @Test
    public void serializesShape() {
        var document = Document.createBoolean(true);

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var document = Document.createBoolean(true);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeBoolean(Schema schema, boolean value) {
                assertThat(schema, equalTo(PreludeSchemas.BOOLEAN));
                assertThat(value, equalTo(true));
            }
        };

        document.serializeContents(serializer);
    }
}
