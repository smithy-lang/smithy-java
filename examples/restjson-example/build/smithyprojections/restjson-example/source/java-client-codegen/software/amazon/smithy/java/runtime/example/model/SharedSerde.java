/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;


/**
 * Defines shared serialization and deserialization methods for map and list shapes.
 */
final class SharedSerde {

    static final class MapListStringSerializer implements BiConsumer<Map<String, List<String>>, MapSerializer> {
        static final MapListStringSerializer INSTANCE = new MapListStringSerializer();

        @Override
        public void accept(Map<String, List<String>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    MapListStringValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapListStringValueSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        private static final MapListStringValueSerializer INSTANCE = new MapListStringValueSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {

            serializer.writeList(SharedSchemas.LIST_OF_STRING, values, SharedSerde.ListOfStringSerializer.INSTANCE);
        }
    }

    static Map<String, List<String>> deserializeMapListString(Schema schema, ShapeDeserializer deserializer) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapListStringValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapListStringValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, List<String>>> {
        static final MapListStringValueDeserializer INSTANCE = new MapListStringValueDeserializer();

        @Override
        public void accept(Map<String, List<String>> state, String key, ShapeDeserializer deserializer) {

            state.put(key, SharedSerde.deserializeListOfString(SharedSchemas.LIST_OF_STRING, deserializer));
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

    static List<String> deserializeListOfString(Schema schema, ShapeDeserializer deserializer) {
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

    private SharedSerde() {}
}

