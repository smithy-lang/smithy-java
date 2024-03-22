/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.StreamingTrait;

/**
 * Defines package-private shared shapes across the model package that are not part of another code-generated type.
 */
final class SharedSchemas {

    static final SdkSchema STRING =
            SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();

    static final SdkSchema INTEGER =
            SdkSchema.builder().type(ShapeType.INTEGER).id("smithy.api#Integer").build();

    static final SdkSchema BIRTHDAY = SdkSchema.builder()
            .type(ShapeType.TIMESTAMP)
            .id("smithy.example#Birthday")
            .traits(new SensitiveTrait())
            .build();

    static final SdkSchema STREAM = SdkSchema.builder()
            .type(ShapeType.BLOB)
            .id("smithy.example#Stream")
            .traits(new StreamingTrait())
            .build();

    static final SdkSchema BLOB =
            SdkSchema.builder().type(ShapeType.BLOB).id("smithy.api#Blob").build();

    static final ShapeId MAP_LIST_STRING_ID = ShapeId.from("smithy.api#StringsMap");

    static final ShapeId LIST_OF_STRING_ID = ShapeId.from("smithy.api#ListOfString");

    static final SdkSchema LIST_OF_STRING = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id(LIST_OF_STRING_ID)
            .members(SdkSchema.memberBuilder(0, "member", SharedSchemas.STRING))
            .build();

    static final SdkSchema MAP_LIST_STRING = SdkSchema.builder()
            .type(ShapeType.MAP)
            .id(MAP_LIST_STRING_ID)
            .members(
                    SdkSchema.memberBuilder(0, "key", SharedSchemas.STRING),
                    SdkSchema.memberBuilder(1, "value", SharedSchemas.LIST_OF_STRING))
            .build();

    private SharedSchemas() {}
}
