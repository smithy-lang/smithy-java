/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.codegen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 * Defines shared shapes across the model package that are not part of another code-generated type.
 */
final class SharedSchemas {

    static final SdkSchema SET_OF_STRING = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#SetOfString")
        .traits(
            new UniqueItemsTrait()
        )
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema LIST_OF_STRING_LIST = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#ListOfStringList")
        .members(SdkSchema.memberBuilder("member", SharedSchemas.LIST_OF_STRING))
        .build();

    static final SdkSchema MAP_STRING_STRING = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#MapStringString")
        .members(
                SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder("value", PreludeSchemas.STRING)
        )
        .build();

    static final SdkSchema STREAM = SdkSchema.builder()
        .type(ShapeType.BLOB)
        .id("smithy.example#Stream")
        .build();

    static final SdkSchema LIST_OF_STRING = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#ListOfString")
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING)
        .traits(
            new LengthTrait.Provider().createTrait(
                ShapeId.from("smithy.api#length"),
                Node.objectNodeBuilder()
                    .withMember("min", 1)
                    .build()
            )
        ))
        .build();

    static final SdkSchema MAP_OF_STRING_MAP = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#MapOfStringMap")
        .members(
                SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder("value", SharedSchemas.MAP_STRING_STRING)
        )
        .build();

    static final SdkSchema MAP_LIST_STRING = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#MapListString")
        .members(
                SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder("value", SharedSchemas.LIST_OF_STRING)
        )
        .build();

    static final SdkSchema BIRTHDAY = SdkSchema.builder()
        .type(ShapeType.TIMESTAMP)
        .id("smithy.example#Birthday")
        .traits(
            new TimestampFormatTrait("date-time"),
            new SensitiveTrait()
        )
        .build();

    static SequencedSet<String> deserializeSetOfString(SdkSchema schema, ShapeDeserializer deserializer) {
        SequencedSet<String> result = new LinkedHashSet<>();
        deserializer.readList(schema, elem -> result.add(elem.readString(PreludeSchemas.STRING)));
        return result;
    }

    static List<List<String>> deserializeListOfStringList(SdkSchema schema, ShapeDeserializer deserializer) {
        List<List<String>> result = new ArrayList<>();
        deserializer.readList(schema, elem -> result.add(SharedSchemas.deserializeListOfString(SharedSchemas.LIST_OF_STRING, elem)));
        return result;
    }

    static Map<String, String> deserializeMapStringString(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, String> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, (key, val) -> result.put(key, val.readString(PreludeSchemas.STRING)));
        return result;
    }

    static List<String> deserializeListOfString(SdkSchema schema, ShapeDeserializer deserializer) {
        List<String> result = new ArrayList<>();
        deserializer.readList(schema, elem -> result.add(elem.readString(PreludeSchemas.STRING)));
        return result;
    }

    static Map<String, Map<String, String>> deserializeMapOfStringMap(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, (key, val) -> result.put(key, SharedSchemas.deserializeMapStringString(SharedSchemas.MAP_STRING_STRING, val)));
        return result;
    }

    static Map<String, List<String>> deserializeMapListString(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, (key, val) -> result.put(key, SharedSchemas.deserializeListOfString(SharedSchemas.LIST_OF_STRING, val)));
        return result;
    }

    private SharedSchemas() {}
}

