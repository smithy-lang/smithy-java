/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema.model;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.SensitiveTrait;

final class SharedSchemas {

    static final SdkSchema BIRTHDAY = SdkSchema.builder()
        .type(ShapeType.TIMESTAMP)
        .id("smithy.example#Birthday")
        .traits(new SensitiveTrait())
        .build();

    static final SdkSchema LIST_OF_STRING = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#ListOfString")
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema MAP_LIST_STRING = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#StringsMap")
        .members(
            SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
            SdkSchema.memberBuilder("value", LIST_OF_STRING)
        )
        .build();

    private SharedSchemas() {}
}
