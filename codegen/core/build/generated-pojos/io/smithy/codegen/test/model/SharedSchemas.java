

package io.smithy.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 * Defines shared shapes across the model package that are not part of another code-generated type.
 */
final class SharedSchemas {

    static final SdkSchema LIST_OF_STRING_LIST;
    static final SdkSchema MAP_OF_MAP_LIST;
    static final SdkSchema MAP_OF_STRING_MAP;
    static final SdkSchema LIST_OF_MAPS;
    static final SdkSchema CORRECTED_MAP = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.structures.members#CorrectedMap")
        .members(
                SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder("value", PreludeSchemas.STRING)
        )
        .build();

    static final SdkSchema CORRECTED_LIST = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.structures.members#CorrectedList")
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema MAP_OF_STRING_LIST;
    static final SdkSchema MAP_STRING_STRING = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.structures.members#MapStringString")
        .members(
                SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder("value", PreludeSchemas.STRING)
        )
        .build();

    static final SdkSchema LIST_OF_STRINGS = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.structures.members#ListOfStrings")
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema NESTED_STREAMING_BLOB = SdkSchema.builder()
        .type(ShapeType.BLOB)
        .id("smithy.java.codegen.test.structures.members#NestedStreamingBlob")
        .build();

    static final SdkSchema LIST_OF_LIST_OF_STRING_LIST;
    static final SdkSchema MAP_OF_MAP_OF_STRING_MAP;
    static final SdkSchema LIST_OF_STRUCT = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.structures.members#ListOfStruct")
        .members(SdkSchema.memberBuilder("member", Nested.SCHEMA))
        .build();

    static final SdkSchema STREAMING_BLOB = SdkSchema.builder()
        .type(ShapeType.BLOB)
        .id("smithy.java.codegen.test.structures.members#StreamingBlob")
        .build();

    static final SdkSchema STRING_MAP = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.structures.members#StringMap")
        .members(
                SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder("value", PreludeSchemas.STRING)
        )
        .build();

    static final SdkSchema MAP_LIST;
    static final SdkSchema LIST_OF_STRING = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.structures.members#ListOfString")
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema SET_OF_STRINGS = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.structures.members#SetOfStrings")
        .traits(
            new UniqueItemsTrait()
        )
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final SdkSchema STRING_LIST = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.structures.members#StringList")
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema STRING_STRING_MAP = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.structures.members#StringStringMap")
        .members(
                SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder("value", PreludeSchemas.STRING)
        )
        .build();

    static {
        LIST_OF_STRING_LIST = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.structures.members#ListOfStringList")
            .members(SdkSchema.memberBuilder("member", SharedSchemas.LIST_OF_STRING))
            .build();

        MAP_OF_STRING_MAP = SdkSchema.builder()
            .type(ShapeType.MAP)
            .id("smithy.java.codegen.test.structures.members#MapOfStringMap")
            .members(
                    SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                    SdkSchema.memberBuilder("value", SharedSchemas.STRING_MAP)
            )
            .build();

        LIST_OF_MAPS = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.structures.members#ListOfMaps")
            .members(SdkSchema.memberBuilder("member", SharedSchemas.STRING_STRING_MAP))
            .build();

        MAP_OF_STRING_LIST = SdkSchema.builder()
            .type(ShapeType.MAP)
            .id("smithy.java.codegen.test.structures.members#MapOfStringList")
            .members(
                    SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                    SdkSchema.memberBuilder("value", SharedSchemas.STRING_LIST)
            )
            .build();

        MAP_LIST = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.structures.members#MapList")
            .members(SdkSchema.memberBuilder("member", SharedSchemas.STRING_MAP))
            .build();

        MAP_OF_MAP_LIST = SdkSchema.builder()
            .type(ShapeType.MAP)
            .id("smithy.java.codegen.test.structures.members#MapOfMapList")
            .members(
                    SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                    SdkSchema.memberBuilder("value", SharedSchemas.MAP_LIST)
            )
            .build();

        LIST_OF_LIST_OF_STRING_LIST = SdkSchema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.structures.members#ListOfListOfStringList")
            .members(SdkSchema.memberBuilder("member", SharedSchemas.LIST_OF_STRING_LIST))
            .build();

        MAP_OF_MAP_OF_STRING_MAP = SdkSchema.builder()
            .type(ShapeType.MAP)
            .id("smithy.java.codegen.test.structures.members#MapOfMapOfStringMap")
            .members(
                    SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                    SdkSchema.memberBuilder("value", SharedSchemas.MAP_OF_STRING_MAP)
            )
            .build();

    }

    private SharedSchemas() {}
}

