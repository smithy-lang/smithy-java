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
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class ByteDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.of((byte) 1);

        assertThat(document.type(), equalTo(ShapeType.BYTE));
        assertThat(document.asByte(), equalTo((byte) 1));
        assertThat(document, equalTo(Document.of((byte) 1)));
    }

    @Test
    public void serializesShape() {
        var document = Document.of((byte) 1);

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var document = Document.of((byte) 1);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeByte(SdkSchema schema, byte value) {
                assertThat(schema, equalTo(PreludeSchemas.BYTE));
                assertThat(value, equalTo((byte) 1));
            }
        };

        document.serializeContents(serializer);
    }

    @Test
    public void canWiden() {
        var document = Document.of((byte) 1);

        assertThat(document.asByte(), equalTo((byte) 1));
        assertThat(document.asShort(), equalTo((short) 1));
        assertThat(document.asInteger(), equalTo(1));
        assertThat(document.asLong(), equalTo(1L));
        assertThat(document.asFloat(), equalTo(1f));
        assertThat(document.asDouble(), equalTo(1.0));
    }
}
