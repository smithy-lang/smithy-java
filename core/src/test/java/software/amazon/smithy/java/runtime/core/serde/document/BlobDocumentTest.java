/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class BlobDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.of("hi".getBytes(StandardCharsets.UTF_8));

        assertThat(document.type(), equalTo(ShapeType.BLOB));
        assertThat(document.asBlob(), equalTo("hi".getBytes(StandardCharsets.UTF_8)));
        assertThat(document, equalTo(Document.of("hi".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void serializesShape() {
        var document = Document.of("hi".getBytes(StandardCharsets.UTF_8));

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContents() {
        var document = Document.of("hi".getBytes(StandardCharsets.UTF_8));

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeBlob(SdkSchema schema, byte[] value) {
                assertThat(schema, equalTo(PreludeSchemas.BLOB));
                assertThat(value, equalTo("hi".getBytes(StandardCharsets.UTF_8)));
            }
        };

        document.serializeContents(serializer);
    }
}
