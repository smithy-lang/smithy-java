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

public class IntegerDocumentTest {

    @Test
    public void createsDocument() {
        var document = Document.of(10);

        assertThat(document.type(), equalTo(ShapeType.INTEGER));
        assertThat(document.asInteger(), equalTo(10));
        assertThat(document, equalTo(Document.of(10)));
    }

    @Test
    public void serializesShape() {
        var document = Document.of(10);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeInteger(SdkSchema schema, int value) {
                assertThat(schema, equalTo(PreludeSchemas.INTEGER));
                assertThat(value, equalTo(10));
            }
        };

        document.serialize(serializer);
    }

    @Test
    public void canWidenType() {
        var document = Document.of(10);

        assertThat(document.asLong(), equalTo(10L));
        assertThat(document.asFloat(), equalTo(10f));
        assertThat(document.asDouble(), equalTo(10.0));
    }
}
