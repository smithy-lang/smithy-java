/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ShapeType;

public class SchemaTest {
    @Test
    public void intEnumRequiresIntEnumSchema() {
        Assertions.assertThrows(ApiException.class, () -> {
            Schema.builder()
                .type(ShapeType.STRING)
                .id("smithy.example#Foo")
                .intEnumValues(1, 2, 3)
                .build();
        });
    }

    @Test
    public void enumRequiresStringSchema() {
        Assertions.assertThrows(ApiException.class, () -> {
            Schema.builder()
                .type(ShapeType.INTEGER)
                .id("smithy.example#Foo")
                .stringEnumValues("a", "b")
                .build();
        });
    }

    @Test
    public void enumWorksWithEnumSchema() {
        var schema = Schema.builder()
            .id("smithy.example#Foo")
            .type(ShapeType.ENUM)
            .stringEnumValues("a", "b")
            .build();

        assertThat(schema.stringEnumValues(), containsInAnyOrder("a", "b"));
    }

    @Test
    public void intEnumWorksWithIntEnumSchema() {
        var schema = Schema.builder()
            .id("smithy.example#Foo")
            .type(ShapeType.INT_ENUM)
            .intEnumValues(1, 2, 3)
            .build();

        assertThat(schema.intEnumValues(), containsInAnyOrder(1, 2, 3));
    }
}
