/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;

public final class JsonTestData {

    static final ShapeId BIRD_ID = ShapeId.from("smithy.example#Bird");
    static final SdkSchema BIRD_NAME = SdkSchema.memberBuilder("name", PreludeSchemas.STRING)
        .id(BIRD_ID)
        .build();
    static final SdkSchema BIRD_COLOR = SdkSchema.memberBuilder("color", PreludeSchemas.STRING)
        .id(BIRD_ID)
        .traits(new JsonNameTrait("Color"))
        .build();
    static final SdkSchema BIRD = SdkSchema.builder()
        .id(BIRD_ID)
        .type(ShapeType.STRUCTURE)
        .members(BIRD_NAME, BIRD_COLOR)
        .build();
}
