/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class ShortDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.of((short) 10);

        assertThat(document.type(), equalTo(ShapeType.SHORT));
        assertThat(document.asShort(), equalTo((short) 10));
        assertThat(document, equalTo(Document.of((short) 10)));
    }

    @Test
    public void serializesShape() {
        var document = Document.of((short) 10);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeShort(SdkSchema schema, short value) {
                assertThat(schema, equalTo(PreludeSchemas.SHORT));
                assertThat(value, equalTo((short) 10));
            }
        };

        document.serialize(serializer);
    }

    @Test
    public void canWiden() {
        var document = Document.of((short) 1);

        assertThat(document.asShort(), equalTo((short) 1));
        assertThat(document.asInteger(), equalTo(1));
        assertThat(document.asLong(), equalTo(1L));
        assertThat(document.asFloat(), equalTo(1f));
        assertThat(document.asDouble(), equalTo(1.0));
    }
}
