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
import java.util.Set;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
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

    static final SdkSchema MAP_OF_STRING_MAP = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#MapOfStringMap")
        .members(
                SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder("value", SharedSchemas.MAP_STRING_STRING)
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

    static final SdkSchema MAP_LIST_STRING = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#MapListString")
        .members(
                SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
                SdkSchema.memberBuilder("value", SharedSchemas.LIST_OF_STRING)
        )
        .build();

    static final SdkSchema SET_OF_STRING = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#SetOfString")
        .traits(
            new UniqueItemsTrait()
        )
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema BIRTHDAY = SdkSchema.builder()
        .type(ShapeType.TIMESTAMP)
        .id("smithy.example#Birthday")
        .traits(
            new TimestampFormatTrait("date-time"),
            new SensitiveTrait()
        )
        .build();

    static final class MapOfStringMapSerializer implements BiConsumer<Map<String, Map<String, String>>, MapSerializer> {
        static final MapOfStringMapSerializer INSTANCE = new MapOfStringMapSerializer();

        @Override
        public void accept(Map<String, Map<String, String>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    SharedSchemas.MAP_STRING_STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.MapOfStringMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapOfStringMapValueSerializer implements BiConsumer<Map<String, String>, ShapeSerializer> {
        static final MapOfStringMapValueSerializer INSTANCE = new MapOfStringMapValueSerializer();

        @Override
        public void accept(Map<String, String> values, ShapeSerializer serializer) {
            serializer.writeMap(SharedSchemas.MAP_STRING_STRING, values, SharedSchemas.MapStringStringSerializer.INSTANCE);
        }
    }

    static Map<String, Map<String, String>> deserializeMapOfStringMap(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, (mapData, key, val) -> {
            mapData.put(key, SharedSchemas.deserializeMapStringString(SharedSchemas.MAP_STRING_STRING, val));
        });
        return result;
    }

    static final class ListOfStringSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        static final ListOfStringSerializer INSTANCE = new ListOfStringSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeString(PreludeSchemas.STRING, value);
            }
        }
    }

    static List<String> deserializeListOfString(SdkSchema schema, ShapeDeserializer deserializer) {
        List<String> result = new ArrayList<>();
        deserializer.readList(schema, result, (listData, elem) -> {
            listData.add(elem.readString(PreludeSchemas.STRING));
        });
        return result;
    }

    static final class ListOfStringListSerializer implements BiConsumer<List<List<String>>, ShapeSerializer> {
        static final ListOfStringListSerializer INSTANCE = new ListOfStringListSerializer();

        @Override
        public void accept(List<List<String>> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeList(SharedSchemas.LIST_OF_STRING, value, SharedSchemas.ListOfStringSerializer.INSTANCE);
            }
        }
    }

    static List<List<String>> deserializeListOfStringList(SdkSchema schema, ShapeDeserializer deserializer) {
        List<List<String>> result = new ArrayList<>();
        deserializer.readList(schema, result, (listData, elem) -> {
            listData.add(SharedSchemas.deserializeListOfString(SharedSchemas.LIST_OF_STRING, elem));
        });
        return result;
    }

    static final class MapStringStringSerializer implements BiConsumer<Map<String, String>, MapSerializer> {
        static final MapStringStringSerializer INSTANCE = new MapStringStringSerializer();

        @Override
        public void accept(Map<String, String> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.MapStringStringValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapStringStringValueSerializer implements BiConsumer<String, ShapeSerializer> {
        static final MapStringStringValueSerializer INSTANCE = new MapStringStringValueSerializer();

        @Override
        public void accept(String values, ShapeSerializer serializer) {
            serializer.writeString(PreludeSchemas.STRING, values);
        }
    }

    static Map<String, String> deserializeMapStringString(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, String> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, (mapData, key, val) -> {
            mapData.put(key, val.readString(PreludeSchemas.STRING));
        });
        return result;
    }

    static final class MapListStringSerializer implements BiConsumer<Map<String, List<String>>, MapSerializer> {
        static final MapListStringSerializer INSTANCE = new MapListStringSerializer();

        @Override
        public void accept(Map<String, List<String>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    SharedSchemas.LIST_OF_STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.ListOfStringSerializer.INSTANCE
                );
            }
        }
    }

    static Map<String, List<String>> deserializeMapListString(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, (mapData, key, val) -> {
            mapData.put(key, SharedSchemas.deserializeListOfString(SharedSchemas.LIST_OF_STRING, val));
        });
        return result;
    }

    static final class SetOfStringSerializer implements BiConsumer<Set<String>, ShapeSerializer> {
        static final SetOfStringSerializer INSTANCE = new SetOfStringSerializer();

        @Override
        public void accept(Set<String> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeString(PreludeSchemas.STRING, value);
            }
        }
    }

    static Set<String> deserializeSetOfString(SdkSchema schema, ShapeDeserializer deserializer) {
        Set<String> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, (listData, elem) -> {
            listData.add(elem.readString(PreludeSchemas.STRING));
        });
        return result;
    }

    private SharedSchemas() {}
}

