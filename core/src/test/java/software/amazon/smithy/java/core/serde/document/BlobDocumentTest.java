/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static java.nio.ByteBuffer.wrap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class BlobDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.of(wrap("hi".getBytes(StandardCharsets.UTF_8)));

        assertThat(document.type(), equalTo(ShapeType.BLOB));
        assertThat(document.asBlob(), equalTo(wrap("hi".getBytes(StandardCharsets.UTF_8))));
        assertThat(document, equalTo(Document.of(wrap("hi".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void serializesShape() {
        var document = Document.of(wrap("hi".getBytes(StandardCharsets.UTF_8)));

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContents() {
        var document = Document.of("hi".getBytes(StandardCharsets.UTF_8));

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeBlob(Schema schema, ByteBuffer value) {
                assertThat(schema, equalTo(PreludeSchemas.BLOB));
                assertThat(value, equalTo(wrap("hi".getBytes(StandardCharsets.UTF_8))));
            }
        };

        document.serializeContents(serializer);
    }

    @Test
    public void toObjectReturnsSelf() {
        var bytes = "a".getBytes(StandardCharsets.UTF_8);
        var doc = Document.of(bytes);

        assertThat(doc.asObject(), is(wrap(bytes)));
    }
}
