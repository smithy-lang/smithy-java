/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.JsonNameTrait;

public final class JsonTestData {

    static final ShapeId BIRD_ID = ShapeId.from("smithy.example#Bird");
    static final Schema BIRD = Schema.structureBuilder(BIRD_ID)
            .putMember("name", PreludeSchemas.STRING)
            .putMember("color", PreludeSchemas.STRING, new JsonNameTrait("Color"))
            .putMember("nested", PreludeSchemas.STRING)
            .build();
    static final Schema BIRD_NAME = BIRD.member("name");
    static final Schema BIRD_COLOR = BIRD.member("color");
    static final Schema BIRD_NESTED = BIRD.member("nested");

    static final ShapeId NESTED_ID = ShapeId.from("smithy.example#Nested");
    static final Schema NESTED = Schema.structureBuilder(NESTED_ID)
            .putMember("number", PreludeSchemas.INTEGER)
            .build();
    static final Schema NESTED_NUMBER = NESTED.member("number");

    static final ShapeId UNION_ID = ShapeId.from("smithy.example#Union");
    static final Schema UNION = Schema.unionBuilder(UNION_ID)
            .putMember("booleanValue", PreludeSchemas.BOOLEAN)
            .putMember("intValue", PreludeSchemas.INTEGER)
            .build();
    static final Schema UNION_BOOLEAN_VALUE = UNION.member("booleanValue");
    static final Schema UNION_INTEGER_VALUE = UNION.member("intValue");
}
