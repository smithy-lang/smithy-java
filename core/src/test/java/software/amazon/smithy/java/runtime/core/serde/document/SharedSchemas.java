/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.SensitiveTrait;

/**
 * Defines package-private shared shapes across the model package that are not part of another code-generated type.
 */
final class SharedSchemas {

    static final SdkSchema BIRTHDAY = SdkSchema.builder()
        .type(ShapeType.TIMESTAMP)
        .id("smithy.example#Birthday")
        .traits(new SensitiveTrait())
        .build();

    static final SdkSchema LIST_OF_STRING = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#ListOfString")
        .members(SdkSchema.memberBuilder(0, "member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema MAP_LIST_STRING = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#StringsMap")
        .members(
            SdkSchema.memberBuilder(0, "key", PreludeSchemas.STRING),
            SdkSchema.memberBuilder(1, "value", SharedSchemas.LIST_OF_STRING)
        )
        .build();

    private SharedSchemas() {}
}
