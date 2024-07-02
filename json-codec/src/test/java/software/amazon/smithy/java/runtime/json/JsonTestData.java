/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;

public final class JsonTestData {

    static final ShapeId BIRD_ID = ShapeId.from("smithy.example#Bird");
    static final Schema BIRD_NAME = Schema.memberBuilder("name", PreludeSchemas.STRING)
        .id(BIRD_ID)
        .build();
    static final Schema BIRD_COLOR = Schema.memberBuilder("color", PreludeSchemas.STRING)
        .id(BIRD_ID)
        .traits(new JsonNameTrait("Color"))
        .build();
    static final Schema BIRD_NESTED = Schema.memberBuilder("nested", PreludeSchemas.STRING).id(BIRD_ID).build();
    static final Schema BIRD = Schema.builder()
        .id(BIRD_ID)
        .type(ShapeType.STRUCTURE)
        .members(BIRD_NAME, BIRD_COLOR, BIRD_NESTED)
        .build();

    static final ShapeId NESTED_ID = ShapeId.from("smithy.example#Nested");
    static final Schema NESTED_NUMBER = Schema.memberBuilder("number", PreludeSchemas.INTEGER)
        .id(NESTED_ID)
        .build();
    static final Schema NESTED = Schema.builder()
        .id(NESTED_ID)
        .type(ShapeType.STRUCTURE)
        .members(NESTED_NUMBER)
        .build();

    static final ShapeId UNION_ID = ShapeId.from("smithy.example#Union");
    static final Schema UNION_BOOLEAN_VALUE = Schema.memberBuilder("booleanValue", PreludeSchemas.BOOLEAN)
        .id(UNION_ID)
        .build();
    static final Schema UNION_INTEGER_VALUE = Schema.memberBuilder("intValue", PreludeSchemas.INTEGER)
        .id(UNION_ID)
        .build();
    static final Schema UNION = Schema.builder()
        .id(UNION_ID)
        .type(ShapeType.UNION)
        .members(UNION_BOOLEAN_VALUE, UNION_INTEGER_VALUE)
        .build();
}
