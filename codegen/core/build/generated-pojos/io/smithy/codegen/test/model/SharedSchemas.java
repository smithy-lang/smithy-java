

package io.smithy.codegen.test.model;

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

    static final SdkSchema STREAMING_BLOB = SdkSchema.builder()
        .type(ShapeType.BLOB)
        .id("smithy.java.codegen.test.structures.members#StreamingBlob")
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

    static final SdkSchema NESTED_STREAMING_BLOB = SdkSchema.builder()
        .type(ShapeType.BLOB)
        .id("smithy.java.codegen.test.structures.members#NestedStreamingBlob")
        .build();

    static final SdkSchema LIST_OF_STRINGS = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.structures.members#ListOfStrings")
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final SdkSchema LIST_OF_LIST_OF_STRING_LIST;
    static final SdkSchema MAP_OF_MAP_OF_STRING_MAP;
    static final SdkSchema LIST_OF_STRUCT = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.structures.members#ListOfStruct")
        .members(SdkSchema.memberBuilder("member", Nested.SCHEMA))
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
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.STRING))
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
        deserializer.readList(schema, result, ListOfStringListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ListOfStringListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<List<String>>> {
        static final ListOfStringListMemberDeserializer INSTANCE = new ListOfStringListMemberDeserializer();

        @Override
        public void accept(List<List<String>> state, ShapeDeserializer deserializer) {
            state.add(SharedSchemas.deserializeListOfString(SharedSchemas.LIST_OF_STRING, deserializer));
        }
    }

    static final class MapOfMapListSerializer implements BiConsumer<Map<String, List<Map<String, String>>>, MapSerializer> {
        static final MapOfMapListSerializer INSTANCE = new MapOfMapListSerializer();

        @Override
        public void accept(Map<String, List<Map<String, String>>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    SharedSchemas.MAP_LIST,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.MapOfMapListValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapOfMapListValueSerializer implements BiConsumer<List<Map<String, String>>, ShapeSerializer> {
        private static final MapOfMapListValueSerializer INSTANCE = new MapOfMapListValueSerializer();

        @Override
        public void accept(List<Map<String, String>> values, ShapeSerializer serializer) {
            serializer.writeList(SharedSchemas.MAP_LIST, values, SharedSchemas.MapListSerializer.INSTANCE);
        }
    }

    static Map<String, List<Map<String, String>>> deserializeMapOfMapList(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapOfMapListValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapOfMapListValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, List<Map<String, String>>>> {
        static final MapOfMapListValueDeserializer INSTANCE = new MapOfMapListValueDeserializer();

        @Override
        public void accept(Map<String, List<Map<String, String>>> state, String key, ShapeDeserializer deserializer) {
            state.put(key, SharedSchemas.deserializeMapList(SharedSchemas.MAP_LIST, deserializer));
        }
    }

    static final class MapOfStringMapSerializer implements BiConsumer<Map<String, Map<String, String>>, MapSerializer> {
        static final MapOfStringMapSerializer INSTANCE = new MapOfStringMapSerializer();

        @Override
        public void accept(Map<String, Map<String, String>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    SharedSchemas.STRING_MAP,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.MapOfStringMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapOfStringMapValueSerializer implements BiConsumer<Map<String, String>, ShapeSerializer> {
        private static final MapOfStringMapValueSerializer INSTANCE = new MapOfStringMapValueSerializer();

        @Override
        public void accept(Map<String, String> values, ShapeSerializer serializer) {
            serializer.writeMap(SharedSchemas.STRING_MAP, values, SharedSchemas.StringMapSerializer.INSTANCE);
        }
    }

    static Map<String, Map<String, String>> deserializeMapOfStringMap(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapOfStringMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapOfStringMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Map<String, String>>> {
        static final MapOfStringMapValueDeserializer INSTANCE = new MapOfStringMapValueDeserializer();

        @Override
        public void accept(Map<String, Map<String, String>> state, String key, ShapeDeserializer deserializer) {
            state.put(key, SharedSchemas.deserializeStringMap(SharedSchemas.STRING_MAP, deserializer));
        }
    }

    static final class ListOfMapsSerializer implements BiConsumer<List<Map<String, String>>, ShapeSerializer> {
        static final ListOfMapsSerializer INSTANCE = new ListOfMapsSerializer();

        @Override
        public void accept(List<Map<String, String>> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeMap(SharedSchemas.STRING_STRING_MAP, value, SharedSchemas.StringStringMapSerializer.INSTANCE);
            }
        }
    }

    static List<Map<String, String>> deserializeListOfMaps(SdkSchema schema, ShapeDeserializer deserializer) {
        List<Map<String, String>> result = new ArrayList<>();
        deserializer.readList(schema, result, ListOfMapsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ListOfMapsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Map<String, String>>> {
        static final ListOfMapsMemberDeserializer INSTANCE = new ListOfMapsMemberDeserializer();

        @Override
        public void accept(List<Map<String, String>> state, ShapeDeserializer deserializer) {
            state.add(SharedSchemas.deserializeStringStringMap(SharedSchemas.STRING_STRING_MAP, deserializer));
        }
    }

    static final class CorrectedMapSerializer implements BiConsumer<Map<String, String>, MapSerializer> {
        static final CorrectedMapSerializer INSTANCE = new CorrectedMapSerializer();

        @Override
        public void accept(Map<String, String> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.CorrectedMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class CorrectedMapValueSerializer implements BiConsumer<String, ShapeSerializer> {
        private static final CorrectedMapValueSerializer INSTANCE = new CorrectedMapValueSerializer();

        @Override
        public void accept(String values, ShapeSerializer serializer) {
            serializer.writeString(PreludeSchemas.STRING, values);
        }
    }

    static Map<String, String> deserializeCorrectedMap(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, String> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, CorrectedMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class CorrectedMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, String>> {
        static final CorrectedMapValueDeserializer INSTANCE = new CorrectedMapValueDeserializer();

        @Override
        public void accept(Map<String, String> state, String key, ShapeDeserializer deserializer) {
            state.put(key, deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class CorrectedListSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        static final CorrectedListSerializer INSTANCE = new CorrectedListSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeString(PreludeSchemas.STRING, value);
            }
        }
    }

    static List<String> deserializeCorrectedList(SdkSchema schema, ShapeDeserializer deserializer) {
        List<String> result = new ArrayList<>();
        deserializer.readList(schema, result, CorrectedListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class CorrectedListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<String>> {
        static final CorrectedListMemberDeserializer INSTANCE = new CorrectedListMemberDeserializer();

        @Override
        public void accept(List<String> state, ShapeDeserializer deserializer) {
            state.add(deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class MapOfStringListSerializer implements BiConsumer<Map<String, List<String>>, MapSerializer> {
        static final MapOfStringListSerializer INSTANCE = new MapOfStringListSerializer();

        @Override
        public void accept(Map<String, List<String>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    SharedSchemas.STRING_LIST,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.MapOfStringListValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapOfStringListValueSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        private static final MapOfStringListValueSerializer INSTANCE = new MapOfStringListValueSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {
            serializer.writeList(SharedSchemas.STRING_LIST, values, SharedSchemas.StringListSerializer.INSTANCE);
        }
    }

    static Map<String, List<String>> deserializeMapOfStringList(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapOfStringListValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapOfStringListValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, List<String>>> {
        static final MapOfStringListValueDeserializer INSTANCE = new MapOfStringListValueDeserializer();

        @Override
        public void accept(Map<String, List<String>> state, String key, ShapeDeserializer deserializer) {
            state.put(key, SharedSchemas.deserializeStringList(SharedSchemas.STRING_LIST, deserializer));
        }
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
        private static final MapStringStringValueSerializer INSTANCE = new MapStringStringValueSerializer();

        @Override
        public void accept(String values, ShapeSerializer serializer) {
            serializer.writeString(PreludeSchemas.STRING, values);
        }
    }

    static Map<String, String> deserializeMapStringString(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, String> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapStringStringValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapStringStringValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, String>> {
        static final MapStringStringValueDeserializer INSTANCE = new MapStringStringValueDeserializer();

        @Override
        public void accept(Map<String, String> state, String key, ShapeDeserializer deserializer) {
            state.put(key, deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class ListOfStringsSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        static final ListOfStringsSerializer INSTANCE = new ListOfStringsSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeString(PreludeSchemas.STRING, value);
            }
        }
    }

    static List<String> deserializeListOfStrings(SdkSchema schema, ShapeDeserializer deserializer) {
        List<String> result = new ArrayList<>();
        deserializer.readList(schema, result, ListOfStringsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ListOfStringsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<String>> {
        static final ListOfStringsMemberDeserializer INSTANCE = new ListOfStringsMemberDeserializer();

        @Override
        public void accept(List<String> state, ShapeDeserializer deserializer) {
            state.add(deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class ListOfListOfStringListSerializer implements BiConsumer<List<List<List<String>>>, ShapeSerializer> {
        static final ListOfListOfStringListSerializer INSTANCE = new ListOfListOfStringListSerializer();

        @Override
        public void accept(List<List<List<String>>> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeList(SharedSchemas.LIST_OF_STRING_LIST, value, SharedSchemas.ListOfStringListSerializer.INSTANCE);
            }
        }
    }

    static List<List<List<String>>> deserializeListOfListOfStringList(SdkSchema schema, ShapeDeserializer deserializer) {
        List<List<List<String>>> result = new ArrayList<>();
        deserializer.readList(schema, result, ListOfListOfStringListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ListOfListOfStringListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<List<List<String>>>> {
        static final ListOfListOfStringListMemberDeserializer INSTANCE = new ListOfListOfStringListMemberDeserializer();

        @Override
        public void accept(List<List<List<String>>> state, ShapeDeserializer deserializer) {
            state.add(SharedSchemas.deserializeListOfStringList(SharedSchemas.LIST_OF_STRING_LIST, deserializer));
        }
    }

    static final class MapOfMapOfStringMapSerializer implements BiConsumer<Map<String, Map<String, Map<String, String>>>, MapSerializer> {
        static final MapOfMapOfStringMapSerializer INSTANCE = new MapOfMapOfStringMapSerializer();

        @Override
        public void accept(Map<String, Map<String, Map<String, String>>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    SharedSchemas.MAP_OF_STRING_MAP,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.MapOfMapOfStringMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapOfMapOfStringMapValueSerializer implements BiConsumer<Map<String, Map<String, String>>, ShapeSerializer> {
        private static final MapOfMapOfStringMapValueSerializer INSTANCE = new MapOfMapOfStringMapValueSerializer();

        @Override
        public void accept(Map<String, Map<String, String>> values, ShapeSerializer serializer) {
            serializer.writeMap(SharedSchemas.MAP_OF_STRING_MAP, values, SharedSchemas.MapOfStringMapSerializer.INSTANCE);
        }
    }

    static Map<String, Map<String, Map<String, String>>> deserializeMapOfMapOfStringMap(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, Map<String, Map<String, String>>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapOfMapOfStringMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapOfMapOfStringMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Map<String, Map<String, String>>>> {
        static final MapOfMapOfStringMapValueDeserializer INSTANCE = new MapOfMapOfStringMapValueDeserializer();

        @Override
        public void accept(Map<String, Map<String, Map<String, String>>> state, String key, ShapeDeserializer deserializer) {
            state.put(key, SharedSchemas.deserializeMapOfStringMap(SharedSchemas.MAP_OF_STRING_MAP, deserializer));
        }
    }

    static final class ListOfStructSerializer implements BiConsumer<List<Nested>, ShapeSerializer> {
        static final ListOfStructSerializer INSTANCE = new ListOfStructSerializer();

        @Override
        public void accept(List<Nested> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeStruct(Nested.SCHEMA, value, Nested.InnerSerializer.INSTANCE);
            }
        }
    }

    static List<Nested> deserializeListOfStruct(SdkSchema schema, ShapeDeserializer deserializer) {
        List<Nested> result = new ArrayList<>();
        deserializer.readList(schema, result, ListOfStructMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ListOfStructMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Nested>> {
        static final ListOfStructMemberDeserializer INSTANCE = new ListOfStructMemberDeserializer();

        @Override
        public void accept(List<Nested> state, ShapeDeserializer deserializer) {
            state.add(Nested.builder().deserialize(deserializer).build());
        }
    }

    static final class StringMapSerializer implements BiConsumer<Map<String, String>, MapSerializer> {
        static final StringMapSerializer INSTANCE = new StringMapSerializer();

        @Override
        public void accept(Map<String, String> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.StringMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringMapValueSerializer implements BiConsumer<String, ShapeSerializer> {
        private static final StringMapValueSerializer INSTANCE = new StringMapValueSerializer();

        @Override
        public void accept(String values, ShapeSerializer serializer) {
            serializer.writeString(PreludeSchemas.STRING, values);
        }
    }

    static Map<String, String> deserializeStringMap(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, String> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, String>> {
        static final StringMapValueDeserializer INSTANCE = new StringMapValueDeserializer();

        @Override
        public void accept(Map<String, String> state, String key, ShapeDeserializer deserializer) {
            state.put(key, deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class MapListSerializer implements BiConsumer<List<Map<String, String>>, ShapeSerializer> {
        static final MapListSerializer INSTANCE = new MapListSerializer();

        @Override
        public void accept(List<Map<String, String>> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeMap(SharedSchemas.STRING_MAP, value, SharedSchemas.StringMapSerializer.INSTANCE);
            }
        }
    }

    static List<Map<String, String>> deserializeMapList(SdkSchema schema, ShapeDeserializer deserializer) {
        List<Map<String, String>> result = new ArrayList<>();
        deserializer.readList(schema, result, MapListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class MapListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Map<String, String>>> {
        static final MapListMemberDeserializer INSTANCE = new MapListMemberDeserializer();

        @Override
        public void accept(List<Map<String, String>> state, ShapeDeserializer deserializer) {
            state.add(SharedSchemas.deserializeStringMap(SharedSchemas.STRING_MAP, deserializer));
        }
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
        deserializer.readList(schema, result, ListOfStringMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ListOfStringMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<String>> {
        static final ListOfStringMemberDeserializer INSTANCE = new ListOfStringMemberDeserializer();

        @Override
        public void accept(List<String> state, ShapeDeserializer deserializer) {
            state.add(deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class SetOfStringsSerializer implements BiConsumer<Set<String>, ShapeSerializer> {
        static final SetOfStringsSerializer INSTANCE = new SetOfStringsSerializer();

        @Override
        public void accept(Set<String> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeString(PreludeSchemas.STRING, value);
            }
        }
    }

    static Set<String> deserializeSetOfStrings(SdkSchema schema, ShapeDeserializer deserializer) {
        Set<String> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfStringsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfStringsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<String>> {
        static final SetOfStringsMemberDeserializer INSTANCE = new SetOfStringsMemberDeserializer();

        @Override
        public void accept(Set<String> state, ShapeDeserializer deserializer) {
            state.add(deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class StringListSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        static final StringListSerializer INSTANCE = new StringListSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeString(PreludeSchemas.STRING, value);
            }
        }
    }

    static List<String> deserializeStringList(SdkSchema schema, ShapeDeserializer deserializer) {
        List<String> result = new ArrayList<>();
        deserializer.readList(schema, result, StringListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class StringListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<String>> {
        static final StringListMemberDeserializer INSTANCE = new StringListMemberDeserializer();

        @Override
        public void accept(List<String> state, ShapeDeserializer deserializer) {
            state.add(deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class StringStringMapSerializer implements BiConsumer<Map<String, String>, MapSerializer> {
        static final StringStringMapSerializer INSTANCE = new StringStringMapSerializer();

        @Override
        public void accept(Map<String, String> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SharedSchemas.StringStringMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringStringMapValueSerializer implements BiConsumer<String, ShapeSerializer> {
        private static final StringStringMapValueSerializer INSTANCE = new StringStringMapValueSerializer();

        @Override
        public void accept(String values, ShapeSerializer serializer) {
            serializer.writeString(PreludeSchemas.STRING, values);
        }
    }

    static Map<String, String> deserializeStringStringMap(SdkSchema schema, ShapeDeserializer deserializer) {
        Map<String, String> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringStringMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringStringMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, String>> {
        static final StringStringMapValueDeserializer INSTANCE = new StringStringMapValueDeserializer();

        @Override
        public void accept(Map<String, String> state, String key, ShapeDeserializer deserializer) {
            state.put(key, deserializer.readString(PreludeSchemas.STRING));
        }
    }

    private SharedSchemas() {}
}

