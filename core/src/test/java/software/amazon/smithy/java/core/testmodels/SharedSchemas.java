/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.testmodels;

import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.SensitiveTrait;

/**
 * Defines package-private shared shapes across the model package that are not part of another code-generated type.
 */
public final class SharedSchemas {

    public static final Schema BIRTHDAY = Schema.createTimestamp(
        ShapeId.from("smithy.example#Birthday"),
        new SensitiveTrait()
    );

    public static final Schema LIST_OF_STRING = Schema.listBuilder(ShapeId.from("smithy.example#ListOfString"))
        .putMember("member", PreludeSchemas.STRING)
        .build();

    public static final Schema MAP_LIST_STRING = Schema.mapBuilder(ShapeId.from("smithy.example#StringsMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", LIST_OF_STRING)
        .build();

    private SharedSchemas() {}
}
