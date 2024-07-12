/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.model;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;

/**
 * Defines shared shapes across the model package that are not part of another code-generated type.
 */
final class SharedSchemas {
    static final Schema BIRTHDAY = Schema.createTimestamp(ShapeId.from("smithy.example#Birthday"),
        new SensitiveTrait());
    static final Schema STREAM = Schema.createBlob(ShapeId.from("smithy.example#Stream"));
    static final Schema LIST_OF_STRING = Schema.listBuilder(ShapeId.from("smithy.example#ListOfString"))
        .putMember("member", PreludeSchemas.STRING,
            LengthTrait.builder().min(1L).build())
        .build();

    static final Schema MAP_LIST_STRING = Schema.mapBuilder(ShapeId.from("smithy.example#MapListString"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", SharedSchemas.LIST_OF_STRING)
        .build();

    private SharedSchemas() {}
}

