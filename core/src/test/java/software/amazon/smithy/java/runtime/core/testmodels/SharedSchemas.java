/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.testmodels;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.SensitiveTrait;

/**
 * Defines package-private shared shapes across the model package that are not part of another code-generated type.
 */
public final class SharedSchemas {

    public static final Schema BIRTHDAY = Schema.builder()
        .type(ShapeType.TIMESTAMP)
        .id("smithy.example#Birthday")
        .traits(new SensitiveTrait())
        .build();

    public static final Schema LIST_OF_STRING = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#ListOfString")
        .members(Schema.memberBuilder(0, "member", PreludeSchemas.STRING))
        .build();

    public static final Schema MAP_LIST_STRING = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#StringsMap")
        .members(
            Schema.memberBuilder(0, "key", PreludeSchemas.STRING),
            Schema.memberBuilder(1, "value", SharedSchemas.LIST_OF_STRING)
        )
        .build();

    private SharedSchemas() {}
}
