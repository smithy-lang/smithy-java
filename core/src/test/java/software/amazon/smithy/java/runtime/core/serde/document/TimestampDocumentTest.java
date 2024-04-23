/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class TimestampDocumentTest {

    private Instant getTestTime() {
        LocalDate date = LocalDate.of(2006, 3, 1);
        return date.atStartOfDay(ZoneId.of("UTC")).toInstant();
    }

    @Test
    public void createsDocument() {
        var time = getTestTime();
        var document = Document.createTimestamp(time);

        assertThat(document.type(), equalTo(ShapeType.TIMESTAMP));
        assertThat(document.asTimestamp(), equalTo(time));
        assertThat(document, equalTo(Document.createTimestamp(time)));
    }

    @Test
    public void serializesShape() {
        var document = Document.createTimestamp(getTestTime());

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(SdkSchema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        var time = getTestTime();
        var document = Document.createTimestamp(time);

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeTimestamp(SdkSchema schema, Instant value) {
                assertThat(schema, equalTo(PreludeSchemas.TIMESTAMP));
                assertThat(value, equalTo(time));
            }
        };

        document.serializeContents(serializer);
    }
}
