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

public class StringDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.of("hi");

        assertThat(document.type(), equalTo(ShapeType.STRING));
        assertThat(document.asString(), equalTo("hi"));
        assertThat(document, equalTo(Document.of("hi")));
    }

    @Test
    public void serializesShape() {
        var document = Document.of("hi");

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var document = Document.of("hi");

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeString(Schema schema, String value) {
                assertThat(schema, equalTo(PreludeSchemas.STRING));
                assertThat(value, equalTo("hi"));
            }
        };

        document.serializeContents(serializer);
    }
}
