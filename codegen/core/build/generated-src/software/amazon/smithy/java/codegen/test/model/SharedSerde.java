

package software.amazon.smithy.java.codegen.test.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;


/**
 * Defines shared serialization and deserialization methods for map and list shapes.
 */
final class SharedSerde {

    static final class RecursiveMapSerializer implements BiConsumer<Map<String, IntermediateMapStructure>, MapSerializer> {
        static final RecursiveMapSerializer INSTANCE = new RecursiveMapSerializer();

        @Override
        public void accept(Map<String, IntermediateMapStructure> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    RecursiveMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class RecursiveMapValueSerializer implements BiConsumer<IntermediateMapStructure, ShapeSerializer> {
        private static final RecursiveMapValueSerializer INSTANCE = new RecursiveMapValueSerializer();

        @Override
        public void accept(IntermediateMapStructure values, ShapeSerializer serializer) {

            serializer.writeStruct(IntermediateMapStructure.SCHEMA, values);
        }
    }

    static Map<String, IntermediateMapStructure> deserializeRecursiveMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, IntermediateMapStructure> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, RecursiveMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class RecursiveMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, IntermediateMapStructure>> {
        static final RecursiveMapValueDeserializer INSTANCE = new RecursiveMapValueDeserializer();

        @Override
        public void accept(Map<String, IntermediateMapStructure> state, String key, ShapeDeserializer deserializer) {

            state.put(key, IntermediateMapStructure.builder().deserialize(deserializer).build());
        }
    }

    static final class RecursiveListSerializer implements BiConsumer<List<IntermediateListStructure>, ShapeSerializer> {
        static final RecursiveListSerializer INSTANCE = new RecursiveListSerializer();

        @Override
        public void accept(List<IntermediateListStructure> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeStruct(IntermediateListStructure.SCHEMA, value);
            }
        }
    }

    static List<IntermediateListStructure> deserializeRecursiveList(Schema schema, ShapeDeserializer deserializer) {
        List<IntermediateListStructure> result = new ArrayList<>();
        deserializer.readList(schema, result, RecursiveListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class RecursiveListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<IntermediateListStructure>> {
        static final RecursiveListMemberDeserializer INSTANCE = new RecursiveListMemberDeserializer();

        @Override
        public void accept(List<IntermediateListStructure> state, ShapeDeserializer deserializer) {

            state.add(IntermediateListStructure.builder().deserialize(deserializer).build());
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
                    MapStringStringValueSerializer.INSTANCE
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

    static Map<String, String> deserializeMapStringString(Schema schema, ShapeDeserializer deserializer) {
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

    static List<String> deserializeListOfStrings(Schema schema, ShapeDeserializer deserializer) {
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

    static final class SparseStringUnionMapSerializer implements BiConsumer<Map<String, NestedUnion>, MapSerializer> {
        static final SparseStringUnionMapSerializer INSTANCE = new SparseStringUnionMapSerializer();

        @Override
        public void accept(Map<String, NestedUnion> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringUnionMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringUnionMapValueSerializer implements BiConsumer<NestedUnion, ShapeSerializer> {
        private static final SparseStringUnionMapValueSerializer INSTANCE = new SparseStringUnionMapValueSerializer();

        @Override
        public void accept(NestedUnion values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(NestedUnion.SCHEMA);
                return;
            }
            serializer.writeStruct(NestedUnion.SCHEMA, values);
        }
    }

    static Map<String, NestedUnion> deserializeSparseStringUnionMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, NestedUnion> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringUnionMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringUnionMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, NestedUnion>> {
        static final SparseStringUnionMapValueDeserializer INSTANCE = new SparseStringUnionMapValueDeserializer();

        @Override
        public void accept(Map<String, NestedUnion> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, NestedUnion.builder().deserialize(deserializer).build());
        }
    }

    static final class SparseStringTimestampMapSerializer implements BiConsumer<Map<String, Instant>, MapSerializer> {
        static final SparseStringTimestampMapSerializer INSTANCE = new SparseStringTimestampMapSerializer();

        @Override
        public void accept(Map<String, Instant> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringTimestampMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringTimestampMapValueSerializer implements BiConsumer<Instant, ShapeSerializer> {
        private static final SparseStringTimestampMapValueSerializer INSTANCE = new SparseStringTimestampMapValueSerializer();

        @Override
        public void accept(Instant values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.TIMESTAMP);
                return;
            }
            serializer.writeTimestamp(PreludeSchemas.TIMESTAMP, values);
        }
    }

    static Map<String, Instant> deserializeSparseStringTimestampMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Instant> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringTimestampMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringTimestampMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Instant>> {
        static final SparseStringTimestampMapValueDeserializer INSTANCE = new SparseStringTimestampMapValueDeserializer();

        @Override
        public void accept(Map<String, Instant> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readTimestamp(PreludeSchemas.TIMESTAMP));
        }
    }

    static final class SparseStringStructMapSerializer implements BiConsumer<Map<String, NestedStruct>, MapSerializer> {
        static final SparseStringStructMapSerializer INSTANCE = new SparseStringStructMapSerializer();

        @Override
        public void accept(Map<String, NestedStruct> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringStructMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringStructMapValueSerializer implements BiConsumer<NestedStruct, ShapeSerializer> {
        private static final SparseStringStructMapValueSerializer INSTANCE = new SparseStringStructMapValueSerializer();

        @Override
        public void accept(NestedStruct values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(NestedStruct.SCHEMA);
                return;
            }
            serializer.writeStruct(NestedStruct.SCHEMA, values);
        }
    }

    static Map<String, NestedStruct> deserializeSparseStringStructMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, NestedStruct> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringStructMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringStructMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, NestedStruct>> {
        static final SparseStringStructMapValueDeserializer INSTANCE = new SparseStringStructMapValueDeserializer();

        @Override
        public void accept(Map<String, NestedStruct> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, NestedStruct.builder().deserialize(deserializer).build());
        }
    }

    static final class SparseStringStringMapSerializer implements BiConsumer<Map<String, String>, MapSerializer> {
        static final SparseStringStringMapSerializer INSTANCE = new SparseStringStringMapSerializer();

        @Override
        public void accept(Map<String, String> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringStringMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringStringMapValueSerializer implements BiConsumer<String, ShapeSerializer> {
        private static final SparseStringStringMapValueSerializer INSTANCE = new SparseStringStringMapValueSerializer();

        @Override
        public void accept(String values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.STRING);
                return;
            }
            serializer.writeString(PreludeSchemas.STRING, values);
        }
    }

    static Map<String, String> deserializeSparseStringStringMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, String> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringStringMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringStringMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, String>> {
        static final SparseStringStringMapValueDeserializer INSTANCE = new SparseStringStringMapValueDeserializer();

        @Override
        public void accept(Map<String, String> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class SparseStringShortMapSerializer implements BiConsumer<Map<String, Short>, MapSerializer> {
        static final SparseStringShortMapSerializer INSTANCE = new SparseStringShortMapSerializer();

        @Override
        public void accept(Map<String, Short> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringShortMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringShortMapValueSerializer implements BiConsumer<Short, ShapeSerializer> {
        private static final SparseStringShortMapValueSerializer INSTANCE = new SparseStringShortMapValueSerializer();

        @Override
        public void accept(Short values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.SHORT);
                return;
            }
            serializer.writeShort(PreludeSchemas.SHORT, values);
        }
    }

    static Map<String, Short> deserializeSparseStringShortMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Short> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringShortMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringShortMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Short>> {
        static final SparseStringShortMapValueDeserializer INSTANCE = new SparseStringShortMapValueDeserializer();

        @Override
        public void accept(Map<String, Short> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readShort(PreludeSchemas.SHORT));
        }
    }

    static final class SparseStringLongMapSerializer implements BiConsumer<Map<String, Long>, MapSerializer> {
        static final SparseStringLongMapSerializer INSTANCE = new SparseStringLongMapSerializer();

        @Override
        public void accept(Map<String, Long> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringLongMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringLongMapValueSerializer implements BiConsumer<Long, ShapeSerializer> {
        private static final SparseStringLongMapValueSerializer INSTANCE = new SparseStringLongMapValueSerializer();

        @Override
        public void accept(Long values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.LONG);
                return;
            }
            serializer.writeLong(PreludeSchemas.LONG, values);
        }
    }

    static Map<String, Long> deserializeSparseStringLongMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Long> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringLongMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringLongMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Long>> {
        static final SparseStringLongMapValueDeserializer INSTANCE = new SparseStringLongMapValueDeserializer();

        @Override
        public void accept(Map<String, Long> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readLong(PreludeSchemas.LONG));
        }
    }

    static final class SparseStringIntEnumMapSerializer implements BiConsumer<Map<String, NestedIntEnum>, MapSerializer> {
        static final SparseStringIntEnumMapSerializer INSTANCE = new SparseStringIntEnumMapSerializer();

        @Override
        public void accept(Map<String, NestedIntEnum> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringIntEnumMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringIntEnumMapValueSerializer implements BiConsumer<NestedIntEnum, ShapeSerializer> {
        private static final SparseStringIntEnumMapValueSerializer INSTANCE = new SparseStringIntEnumMapValueSerializer();

        @Override
        public void accept(NestedIntEnum values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(NestedIntEnum.SCHEMA);
                return;
            }
            serializer.writeInteger(NestedIntEnum.SCHEMA, values.value());
        }
    }

    static Map<String, NestedIntEnum> deserializeSparseStringIntEnumMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, NestedIntEnum> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringIntEnumMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringIntEnumMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, NestedIntEnum>> {
        static final SparseStringIntEnumMapValueDeserializer INSTANCE = new SparseStringIntEnumMapValueDeserializer();

        @Override
        public void accept(Map<String, NestedIntEnum> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, NestedIntEnum.builder().deserialize(deserializer).build());
        }
    }

    static final class SparseStringIntegerMapSerializer implements BiConsumer<Map<String, Integer>, MapSerializer> {
        static final SparseStringIntegerMapSerializer INSTANCE = new SparseStringIntegerMapSerializer();

        @Override
        public void accept(Map<String, Integer> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringIntegerMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringIntegerMapValueSerializer implements BiConsumer<Integer, ShapeSerializer> {
        private static final SparseStringIntegerMapValueSerializer INSTANCE = new SparseStringIntegerMapValueSerializer();

        @Override
        public void accept(Integer values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.INTEGER);
                return;
            }
            serializer.writeInteger(PreludeSchemas.INTEGER, values);
        }
    }

    static Map<String, Integer> deserializeSparseStringIntegerMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Integer> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringIntegerMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringIntegerMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Integer>> {
        static final SparseStringIntegerMapValueDeserializer INSTANCE = new SparseStringIntegerMapValueDeserializer();

        @Override
        public void accept(Map<String, Integer> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readInteger(PreludeSchemas.INTEGER));
        }
    }

    static final class SparseStringFloatMapSerializer implements BiConsumer<Map<String, Float>, MapSerializer> {
        static final SparseStringFloatMapSerializer INSTANCE = new SparseStringFloatMapSerializer();

        @Override
        public void accept(Map<String, Float> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringFloatMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringFloatMapValueSerializer implements BiConsumer<Float, ShapeSerializer> {
        private static final SparseStringFloatMapValueSerializer INSTANCE = new SparseStringFloatMapValueSerializer();

        @Override
        public void accept(Float values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.FLOAT);
                return;
            }
            serializer.writeFloat(PreludeSchemas.FLOAT, values);
        }
    }

    static Map<String, Float> deserializeSparseStringFloatMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Float> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringFloatMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringFloatMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Float>> {
        static final SparseStringFloatMapValueDeserializer INSTANCE = new SparseStringFloatMapValueDeserializer();

        @Override
        public void accept(Map<String, Float> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readFloat(PreludeSchemas.FLOAT));
        }
    }

    static final class SparseStringEnumMapSerializer implements BiConsumer<Map<String, NestedEnum>, MapSerializer> {
        static final SparseStringEnumMapSerializer INSTANCE = new SparseStringEnumMapSerializer();

        @Override
        public void accept(Map<String, NestedEnum> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringEnumMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringEnumMapValueSerializer implements BiConsumer<NestedEnum, ShapeSerializer> {
        private static final SparseStringEnumMapValueSerializer INSTANCE = new SparseStringEnumMapValueSerializer();

        @Override
        public void accept(NestedEnum values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(NestedEnum.SCHEMA);
                return;
            }
            serializer.writeString(NestedEnum.SCHEMA, values.value());
        }
    }

    static Map<String, NestedEnum> deserializeSparseStringEnumMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, NestedEnum> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringEnumMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringEnumMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, NestedEnum>> {
        static final SparseStringEnumMapValueDeserializer INSTANCE = new SparseStringEnumMapValueDeserializer();

        @Override
        public void accept(Map<String, NestedEnum> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, NestedEnum.builder().deserialize(deserializer).build());
        }
    }

    static final class SparseStringDoubleMapSerializer implements BiConsumer<Map<String, Double>, MapSerializer> {
        static final SparseStringDoubleMapSerializer INSTANCE = new SparseStringDoubleMapSerializer();

        @Override
        public void accept(Map<String, Double> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringDoubleMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringDoubleMapValueSerializer implements BiConsumer<Double, ShapeSerializer> {
        private static final SparseStringDoubleMapValueSerializer INSTANCE = new SparseStringDoubleMapValueSerializer();

        @Override
        public void accept(Double values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.DOUBLE);
                return;
            }
            serializer.writeDouble(PreludeSchemas.DOUBLE, values);
        }
    }

    static Map<String, Double> deserializeSparseStringDoubleMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Double> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringDoubleMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringDoubleMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Double>> {
        static final SparseStringDoubleMapValueDeserializer INSTANCE = new SparseStringDoubleMapValueDeserializer();

        @Override
        public void accept(Map<String, Double> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readDouble(PreludeSchemas.DOUBLE));
        }
    }

    static final class SparseStringByteMapSerializer implements BiConsumer<Map<String, Byte>, MapSerializer> {
        static final SparseStringByteMapSerializer INSTANCE = new SparseStringByteMapSerializer();

        @Override
        public void accept(Map<String, Byte> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringByteMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringByteMapValueSerializer implements BiConsumer<Byte, ShapeSerializer> {
        private static final SparseStringByteMapValueSerializer INSTANCE = new SparseStringByteMapValueSerializer();

        @Override
        public void accept(Byte values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.BYTE);
                return;
            }
            serializer.writeByte(PreludeSchemas.BYTE, values);
        }
    }

    static Map<String, Byte> deserializeSparseStringByteMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Byte> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringByteMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringByteMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Byte>> {
        static final SparseStringByteMapValueDeserializer INSTANCE = new SparseStringByteMapValueDeserializer();

        @Override
        public void accept(Map<String, Byte> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readByte(PreludeSchemas.BYTE));
        }
    }

    static final class SparseStringBooleanMapSerializer implements BiConsumer<Map<String, Boolean>, MapSerializer> {
        static final SparseStringBooleanMapSerializer INSTANCE = new SparseStringBooleanMapSerializer();

        @Override
        public void accept(Map<String, Boolean> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringBooleanMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringBooleanMapValueSerializer implements BiConsumer<Boolean, ShapeSerializer> {
        private static final SparseStringBooleanMapValueSerializer INSTANCE = new SparseStringBooleanMapValueSerializer();

        @Override
        public void accept(Boolean values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.BOOLEAN);
                return;
            }
            serializer.writeBoolean(PreludeSchemas.BOOLEAN, values);
        }
    }

    static Map<String, Boolean> deserializeSparseStringBooleanMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringBooleanMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringBooleanMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Boolean>> {
        static final SparseStringBooleanMapValueDeserializer INSTANCE = new SparseStringBooleanMapValueDeserializer();

        @Override
        public void accept(Map<String, Boolean> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readBoolean(PreludeSchemas.BOOLEAN));
        }
    }

    static final class SparseStringBlobMapSerializer implements BiConsumer<Map<String, ByteBuffer>, MapSerializer> {
        static final SparseStringBlobMapSerializer INSTANCE = new SparseStringBlobMapSerializer();

        @Override
        public void accept(Map<String, ByteBuffer> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringBlobMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringBlobMapValueSerializer implements BiConsumer<ByteBuffer, ShapeSerializer> {
        private static final SparseStringBlobMapValueSerializer INSTANCE = new SparseStringBlobMapValueSerializer();

        @Override
        public void accept(ByteBuffer values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.BLOB);
                return;
            }
            serializer.writeBlob(PreludeSchemas.BLOB, values);
        }
    }

    static Map<String, ByteBuffer> deserializeSparseStringBlobMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, ByteBuffer> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringBlobMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringBlobMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, ByteBuffer>> {
        static final SparseStringBlobMapValueDeserializer INSTANCE = new SparseStringBlobMapValueDeserializer();

        @Override
        public void accept(Map<String, ByteBuffer> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readBlob(PreludeSchemas.BLOB));
        }
    }

    static final class SparseStringBigIntegerMapSerializer implements BiConsumer<Map<String, BigInteger>, MapSerializer> {
        static final SparseStringBigIntegerMapSerializer INSTANCE = new SparseStringBigIntegerMapSerializer();

        @Override
        public void accept(Map<String, BigInteger> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringBigIntegerMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringBigIntegerMapValueSerializer implements BiConsumer<BigInteger, ShapeSerializer> {
        private static final SparseStringBigIntegerMapValueSerializer INSTANCE = new SparseStringBigIntegerMapValueSerializer();

        @Override
        public void accept(BigInteger values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.BIG_INTEGER);
                return;
            }
            serializer.writeBigInteger(PreludeSchemas.BIG_INTEGER, values);
        }
    }

    static Map<String, BigInteger> deserializeSparseStringBigIntegerMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, BigInteger> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringBigIntegerMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringBigIntegerMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, BigInteger>> {
        static final SparseStringBigIntegerMapValueDeserializer INSTANCE = new SparseStringBigIntegerMapValueDeserializer();

        @Override
        public void accept(Map<String, BigInteger> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readBigInteger(PreludeSchemas.BIG_INTEGER));
        }
    }

    static final class SparseStringBigDecimalMapSerializer implements BiConsumer<Map<String, BigDecimal>, MapSerializer> {
        static final SparseStringBigDecimalMapSerializer INSTANCE = new SparseStringBigDecimalMapSerializer();

        @Override
        public void accept(Map<String, BigDecimal> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseStringBigDecimalMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseStringBigDecimalMapValueSerializer implements BiConsumer<BigDecimal, ShapeSerializer> {
        private static final SparseStringBigDecimalMapValueSerializer INSTANCE = new SparseStringBigDecimalMapValueSerializer();

        @Override
        public void accept(BigDecimal values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(PreludeSchemas.BIG_DECIMAL);
                return;
            }
            serializer.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, values);
        }
    }

    static Map<String, BigDecimal> deserializeSparseStringBigDecimalMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, SparseStringBigDecimalMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringBigDecimalMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, BigDecimal>> {
        static final SparseStringBigDecimalMapValueDeserializer INSTANCE = new SparseStringBigDecimalMapValueDeserializer();

        @Override
        public void accept(Map<String, BigDecimal> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readBigDecimal(PreludeSchemas.BIG_DECIMAL));
        }
    }

    static final class MapOfStringListSerializer implements BiConsumer<Map<String, List<String>>, MapSerializer> {
        static final MapOfStringListSerializer INSTANCE = new MapOfStringListSerializer();

        @Override
        public void accept(Map<String, List<String>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    MapOfStringListValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapOfStringListValueSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        private static final MapOfStringListValueSerializer INSTANCE = new MapOfStringListValueSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {

            serializer.writeList(SharedSchemas.STRING_LIST, values, SharedSerde.StringListSerializer.INSTANCE);
        }
    }

    static Map<String, List<String>> deserializeMapOfStringList(Schema schema, ShapeDeserializer deserializer) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapOfStringListValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapOfStringListValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, List<String>>> {
        static final MapOfStringListValueDeserializer INSTANCE = new MapOfStringListValueDeserializer();

        @Override
        public void accept(Map<String, List<String>> state, String key, ShapeDeserializer deserializer) {

            state.put(key, SharedSerde.deserializeStringList(SharedSchemas.STRING_LIST, deserializer));
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

    static List<String> deserializeStringList(Schema schema, ShapeDeserializer deserializer) {
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

    static final class MapOfMapOfStringMapSerializer implements BiConsumer<Map<String, Map<String, Map<String, String>>>, MapSerializer> {
        static final MapOfMapOfStringMapSerializer INSTANCE = new MapOfMapOfStringMapSerializer();

        @Override
        public void accept(Map<String, Map<String, Map<String, String>>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    MapOfMapOfStringMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapOfMapOfStringMapValueSerializer implements BiConsumer<Map<String, Map<String, String>>, ShapeSerializer> {
        private static final MapOfMapOfStringMapValueSerializer INSTANCE = new MapOfMapOfStringMapValueSerializer();

        @Override
        public void accept(Map<String, Map<String, String>> values, ShapeSerializer serializer) {

            serializer.writeMap(SharedSchemas.MAP_OF_STRING_MAP, values, SharedSerde.MapOfStringMapSerializer.INSTANCE);
        }
    }

    static Map<String, Map<String, Map<String, String>>> deserializeMapOfMapOfStringMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Map<String, Map<String, String>>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapOfMapOfStringMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapOfMapOfStringMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Map<String, Map<String, String>>>> {
        static final MapOfMapOfStringMapValueDeserializer INSTANCE = new MapOfMapOfStringMapValueDeserializer();

        @Override
        public void accept(Map<String, Map<String, Map<String, String>>> state, String key, ShapeDeserializer deserializer) {

            state.put(key, SharedSerde.deserializeMapOfStringMap(SharedSchemas.MAP_OF_STRING_MAP, deserializer));
        }
    }

    static final class MapOfStringMapSerializer implements BiConsumer<Map<String, Map<String, String>>, MapSerializer> {
        static final MapOfStringMapSerializer INSTANCE = new MapOfStringMapSerializer();

        @Override
        public void accept(Map<String, Map<String, String>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    MapOfStringMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapOfStringMapValueSerializer implements BiConsumer<Map<String, String>, ShapeSerializer> {
        private static final MapOfStringMapValueSerializer INSTANCE = new MapOfStringMapValueSerializer();

        @Override
        public void accept(Map<String, String> values, ShapeSerializer serializer) {

            serializer.writeMap(SharedSchemas.STRING_MAP, values, SharedSerde.StringMapSerializer.INSTANCE);
        }
    }

    static Map<String, Map<String, String>> deserializeMapOfStringMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapOfStringMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapOfStringMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Map<String, String>>> {
        static final MapOfStringMapValueDeserializer INSTANCE = new MapOfStringMapValueDeserializer();

        @Override
        public void accept(Map<String, Map<String, String>> state, String key, ShapeDeserializer deserializer) {

            state.put(key, SharedSerde.deserializeStringMap(SharedSchemas.STRING_MAP, deserializer));
        }
    }

    static final class MapOfMapListSerializer implements BiConsumer<Map<String, List<Map<String, String>>>, MapSerializer> {
        static final MapOfMapListSerializer INSTANCE = new MapOfMapListSerializer();

        @Override
        public void accept(Map<String, List<Map<String, String>>> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    MapOfMapListValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class MapOfMapListValueSerializer implements BiConsumer<List<Map<String, String>>, ShapeSerializer> {
        private static final MapOfMapListValueSerializer INSTANCE = new MapOfMapListValueSerializer();

        @Override
        public void accept(List<Map<String, String>> values, ShapeSerializer serializer) {

            serializer.writeList(SharedSchemas.MAP_LIST, values, SharedSerde.MapListSerializer.INSTANCE);
        }
    }

    static Map<String, List<Map<String, String>>> deserializeMapOfMapList(Schema schema, ShapeDeserializer deserializer) {
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, MapOfMapListValueDeserializer.INSTANCE);
        return result;
    }

    private static final class MapOfMapListValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, List<Map<String, String>>>> {
        static final MapOfMapListValueDeserializer INSTANCE = new MapOfMapListValueDeserializer();

        @Override
        public void accept(Map<String, List<Map<String, String>>> state, String key, ShapeDeserializer deserializer) {

            state.put(key, SharedSerde.deserializeMapList(SharedSchemas.MAP_LIST, deserializer));
        }
    }

    static final class MapListSerializer implements BiConsumer<List<Map<String, String>>, ShapeSerializer> {
        static final MapListSerializer INSTANCE = new MapListSerializer();

        @Override
        public void accept(List<Map<String, String>> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeMap(SharedSchemas.STRING_MAP, value, SharedSerde.StringMapSerializer.INSTANCE);
            }
        }
    }

    static List<Map<String, String>> deserializeMapList(Schema schema, ShapeDeserializer deserializer) {
        List<Map<String, String>> result = new ArrayList<>();
        deserializer.readList(schema, result, MapListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class MapListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Map<String, String>>> {
        static final MapListMemberDeserializer INSTANCE = new MapListMemberDeserializer();

        @Override
        public void accept(List<Map<String, String>> state, ShapeDeserializer deserializer) {

            state.add(SharedSerde.deserializeStringMap(SharedSchemas.STRING_MAP, deserializer));
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
                    StringMapValueSerializer.INSTANCE
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

    static Map<String, String> deserializeStringMap(Schema schema, ShapeDeserializer deserializer) {
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

    static final class StringUnionMapSerializer implements BiConsumer<Map<String, NestedUnion>, MapSerializer> {
        static final StringUnionMapSerializer INSTANCE = new StringUnionMapSerializer();

        @Override
        public void accept(Map<String, NestedUnion> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringUnionMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringUnionMapValueSerializer implements BiConsumer<NestedUnion, ShapeSerializer> {
        private static final StringUnionMapValueSerializer INSTANCE = new StringUnionMapValueSerializer();

        @Override
        public void accept(NestedUnion values, ShapeSerializer serializer) {

            serializer.writeStruct(NestedUnion.SCHEMA, values);
        }
    }

    static Map<String, NestedUnion> deserializeStringUnionMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, NestedUnion> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringUnionMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringUnionMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, NestedUnion>> {
        static final StringUnionMapValueDeserializer INSTANCE = new StringUnionMapValueDeserializer();

        @Override
        public void accept(Map<String, NestedUnion> state, String key, ShapeDeserializer deserializer) {

            state.put(key, NestedUnion.builder().deserialize(deserializer).build());
        }
    }

    static final class StringTimestampMapSerializer implements BiConsumer<Map<String, Instant>, MapSerializer> {
        static final StringTimestampMapSerializer INSTANCE = new StringTimestampMapSerializer();

        @Override
        public void accept(Map<String, Instant> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringTimestampMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringTimestampMapValueSerializer implements BiConsumer<Instant, ShapeSerializer> {
        private static final StringTimestampMapValueSerializer INSTANCE = new StringTimestampMapValueSerializer();

        @Override
        public void accept(Instant values, ShapeSerializer serializer) {

            serializer.writeTimestamp(PreludeSchemas.TIMESTAMP, values);
        }
    }

    static Map<String, Instant> deserializeStringTimestampMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Instant> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringTimestampMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringTimestampMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Instant>> {
        static final StringTimestampMapValueDeserializer INSTANCE = new StringTimestampMapValueDeserializer();

        @Override
        public void accept(Map<String, Instant> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readTimestamp(PreludeSchemas.TIMESTAMP));
        }
    }

    static final class StringStructMapSerializer implements BiConsumer<Map<String, NestedStruct>, MapSerializer> {
        static final StringStructMapSerializer INSTANCE = new StringStructMapSerializer();

        @Override
        public void accept(Map<String, NestedStruct> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringStructMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringStructMapValueSerializer implements BiConsumer<NestedStruct, ShapeSerializer> {
        private static final StringStructMapValueSerializer INSTANCE = new StringStructMapValueSerializer();

        @Override
        public void accept(NestedStruct values, ShapeSerializer serializer) {

            serializer.writeStruct(NestedStruct.SCHEMA, values);
        }
    }

    static Map<String, NestedStruct> deserializeStringStructMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, NestedStruct> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringStructMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringStructMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, NestedStruct>> {
        static final StringStructMapValueDeserializer INSTANCE = new StringStructMapValueDeserializer();

        @Override
        public void accept(Map<String, NestedStruct> state, String key, ShapeDeserializer deserializer) {

            state.put(key, NestedStruct.builder().deserialize(deserializer).build());
        }
    }

    static final class StringShortMapSerializer implements BiConsumer<Map<String, Short>, MapSerializer> {
        static final StringShortMapSerializer INSTANCE = new StringShortMapSerializer();

        @Override
        public void accept(Map<String, Short> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringShortMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringShortMapValueSerializer implements BiConsumer<Short, ShapeSerializer> {
        private static final StringShortMapValueSerializer INSTANCE = new StringShortMapValueSerializer();

        @Override
        public void accept(Short values, ShapeSerializer serializer) {

            serializer.writeShort(PreludeSchemas.SHORT, values);
        }
    }

    static Map<String, Short> deserializeStringShortMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Short> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringShortMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringShortMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Short>> {
        static final StringShortMapValueDeserializer INSTANCE = new StringShortMapValueDeserializer();

        @Override
        public void accept(Map<String, Short> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readShort(PreludeSchemas.SHORT));
        }
    }

    static final class StringLongMapSerializer implements BiConsumer<Map<String, Long>, MapSerializer> {
        static final StringLongMapSerializer INSTANCE = new StringLongMapSerializer();

        @Override
        public void accept(Map<String, Long> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringLongMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringLongMapValueSerializer implements BiConsumer<Long, ShapeSerializer> {
        private static final StringLongMapValueSerializer INSTANCE = new StringLongMapValueSerializer();

        @Override
        public void accept(Long values, ShapeSerializer serializer) {

            serializer.writeLong(PreludeSchemas.LONG, values);
        }
    }

    static Map<String, Long> deserializeStringLongMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Long> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringLongMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringLongMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Long>> {
        static final StringLongMapValueDeserializer INSTANCE = new StringLongMapValueDeserializer();

        @Override
        public void accept(Map<String, Long> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readLong(PreludeSchemas.LONG));
        }
    }

    static final class StringIntEnumMapSerializer implements BiConsumer<Map<String, NestedIntEnum>, MapSerializer> {
        static final StringIntEnumMapSerializer INSTANCE = new StringIntEnumMapSerializer();

        @Override
        public void accept(Map<String, NestedIntEnum> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringIntEnumMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringIntEnumMapValueSerializer implements BiConsumer<NestedIntEnum, ShapeSerializer> {
        private static final StringIntEnumMapValueSerializer INSTANCE = new StringIntEnumMapValueSerializer();

        @Override
        public void accept(NestedIntEnum values, ShapeSerializer serializer) {

            serializer.writeInteger(NestedIntEnum.SCHEMA, values.value());
        }
    }

    static Map<String, NestedIntEnum> deserializeStringIntEnumMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, NestedIntEnum> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringIntEnumMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringIntEnumMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, NestedIntEnum>> {
        static final StringIntEnumMapValueDeserializer INSTANCE = new StringIntEnumMapValueDeserializer();

        @Override
        public void accept(Map<String, NestedIntEnum> state, String key, ShapeDeserializer deserializer) {

            state.put(key, NestedIntEnum.builder().deserialize(deserializer).build());
        }
    }

    static final class StringIntegerMapSerializer implements BiConsumer<Map<String, Integer>, MapSerializer> {
        static final StringIntegerMapSerializer INSTANCE = new StringIntegerMapSerializer();

        @Override
        public void accept(Map<String, Integer> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringIntegerMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringIntegerMapValueSerializer implements BiConsumer<Integer, ShapeSerializer> {
        private static final StringIntegerMapValueSerializer INSTANCE = new StringIntegerMapValueSerializer();

        @Override
        public void accept(Integer values, ShapeSerializer serializer) {

            serializer.writeInteger(PreludeSchemas.INTEGER, values);
        }
    }

    static Map<String, Integer> deserializeStringIntegerMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Integer> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringIntegerMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringIntegerMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Integer>> {
        static final StringIntegerMapValueDeserializer INSTANCE = new StringIntegerMapValueDeserializer();

        @Override
        public void accept(Map<String, Integer> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readInteger(PreludeSchemas.INTEGER));
        }
    }

    static final class StringFloatMapSerializer implements BiConsumer<Map<String, Float>, MapSerializer> {
        static final StringFloatMapSerializer INSTANCE = new StringFloatMapSerializer();

        @Override
        public void accept(Map<String, Float> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringFloatMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringFloatMapValueSerializer implements BiConsumer<Float, ShapeSerializer> {
        private static final StringFloatMapValueSerializer INSTANCE = new StringFloatMapValueSerializer();

        @Override
        public void accept(Float values, ShapeSerializer serializer) {

            serializer.writeFloat(PreludeSchemas.FLOAT, values);
        }
    }

    static Map<String, Float> deserializeStringFloatMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Float> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringFloatMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringFloatMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Float>> {
        static final StringFloatMapValueDeserializer INSTANCE = new StringFloatMapValueDeserializer();

        @Override
        public void accept(Map<String, Float> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readFloat(PreludeSchemas.FLOAT));
        }
    }

    static final class StringEnumMapSerializer implements BiConsumer<Map<String, NestedEnum>, MapSerializer> {
        static final StringEnumMapSerializer INSTANCE = new StringEnumMapSerializer();

        @Override
        public void accept(Map<String, NestedEnum> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringEnumMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringEnumMapValueSerializer implements BiConsumer<NestedEnum, ShapeSerializer> {
        private static final StringEnumMapValueSerializer INSTANCE = new StringEnumMapValueSerializer();

        @Override
        public void accept(NestedEnum values, ShapeSerializer serializer) {

            serializer.writeString(NestedEnum.SCHEMA, values.value());
        }
    }

    static Map<String, NestedEnum> deserializeStringEnumMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, NestedEnum> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringEnumMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringEnumMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, NestedEnum>> {
        static final StringEnumMapValueDeserializer INSTANCE = new StringEnumMapValueDeserializer();

        @Override
        public void accept(Map<String, NestedEnum> state, String key, ShapeDeserializer deserializer) {

            state.put(key, NestedEnum.builder().deserialize(deserializer).build());
        }
    }

    static final class StringDoubleMapSerializer implements BiConsumer<Map<String, Double>, MapSerializer> {
        static final StringDoubleMapSerializer INSTANCE = new StringDoubleMapSerializer();

        @Override
        public void accept(Map<String, Double> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringDoubleMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringDoubleMapValueSerializer implements BiConsumer<Double, ShapeSerializer> {
        private static final StringDoubleMapValueSerializer INSTANCE = new StringDoubleMapValueSerializer();

        @Override
        public void accept(Double values, ShapeSerializer serializer) {

            serializer.writeDouble(PreludeSchemas.DOUBLE, values);
        }
    }

    static Map<String, Double> deserializeStringDoubleMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Double> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringDoubleMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringDoubleMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Double>> {
        static final StringDoubleMapValueDeserializer INSTANCE = new StringDoubleMapValueDeserializer();

        @Override
        public void accept(Map<String, Double> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readDouble(PreludeSchemas.DOUBLE));
        }
    }

    static final class StringByteMapSerializer implements BiConsumer<Map<String, Byte>, MapSerializer> {
        static final StringByteMapSerializer INSTANCE = new StringByteMapSerializer();

        @Override
        public void accept(Map<String, Byte> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringByteMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringByteMapValueSerializer implements BiConsumer<Byte, ShapeSerializer> {
        private static final StringByteMapValueSerializer INSTANCE = new StringByteMapValueSerializer();

        @Override
        public void accept(Byte values, ShapeSerializer serializer) {

            serializer.writeByte(PreludeSchemas.BYTE, values);
        }
    }

    static Map<String, Byte> deserializeStringByteMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Byte> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringByteMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringByteMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Byte>> {
        static final StringByteMapValueDeserializer INSTANCE = new StringByteMapValueDeserializer();

        @Override
        public void accept(Map<String, Byte> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readByte(PreludeSchemas.BYTE));
        }
    }

    static final class StringBooleanMapSerializer implements BiConsumer<Map<String, Boolean>, MapSerializer> {
        static final StringBooleanMapSerializer INSTANCE = new StringBooleanMapSerializer();

        @Override
        public void accept(Map<String, Boolean> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringBooleanMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringBooleanMapValueSerializer implements BiConsumer<Boolean, ShapeSerializer> {
        private static final StringBooleanMapValueSerializer INSTANCE = new StringBooleanMapValueSerializer();

        @Override
        public void accept(Boolean values, ShapeSerializer serializer) {

            serializer.writeBoolean(PreludeSchemas.BOOLEAN, values);
        }
    }

    static Map<String, Boolean> deserializeStringBooleanMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringBooleanMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringBooleanMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Boolean>> {
        static final StringBooleanMapValueDeserializer INSTANCE = new StringBooleanMapValueDeserializer();

        @Override
        public void accept(Map<String, Boolean> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readBoolean(PreludeSchemas.BOOLEAN));
        }
    }

    static final class StringBlobMapSerializer implements BiConsumer<Map<String, ByteBuffer>, MapSerializer> {
        static final StringBlobMapSerializer INSTANCE = new StringBlobMapSerializer();

        @Override
        public void accept(Map<String, ByteBuffer> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringBlobMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringBlobMapValueSerializer implements BiConsumer<ByteBuffer, ShapeSerializer> {
        private static final StringBlobMapValueSerializer INSTANCE = new StringBlobMapValueSerializer();

        @Override
        public void accept(ByteBuffer values, ShapeSerializer serializer) {

            serializer.writeBlob(PreludeSchemas.BLOB, values);
        }
    }

    static Map<String, ByteBuffer> deserializeStringBlobMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, ByteBuffer> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringBlobMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringBlobMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, ByteBuffer>> {
        static final StringBlobMapValueDeserializer INSTANCE = new StringBlobMapValueDeserializer();

        @Override
        public void accept(Map<String, ByteBuffer> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readBlob(PreludeSchemas.BLOB));
        }
    }

    static final class StringBigIntegerMapSerializer implements BiConsumer<Map<String, BigInteger>, MapSerializer> {
        static final StringBigIntegerMapSerializer INSTANCE = new StringBigIntegerMapSerializer();

        @Override
        public void accept(Map<String, BigInteger> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringBigIntegerMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringBigIntegerMapValueSerializer implements BiConsumer<BigInteger, ShapeSerializer> {
        private static final StringBigIntegerMapValueSerializer INSTANCE = new StringBigIntegerMapValueSerializer();

        @Override
        public void accept(BigInteger values, ShapeSerializer serializer) {

            serializer.writeBigInteger(PreludeSchemas.BIG_INTEGER, values);
        }
    }

    static Map<String, BigInteger> deserializeStringBigIntegerMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, BigInteger> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringBigIntegerMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringBigIntegerMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, BigInteger>> {
        static final StringBigIntegerMapValueDeserializer INSTANCE = new StringBigIntegerMapValueDeserializer();

        @Override
        public void accept(Map<String, BigInteger> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readBigInteger(PreludeSchemas.BIG_INTEGER));
        }
    }

    static final class StringBigDecimalMapSerializer implements BiConsumer<Map<String, BigDecimal>, MapSerializer> {
        static final StringBigDecimalMapSerializer INSTANCE = new StringBigDecimalMapSerializer();

        @Override
        public void accept(Map<String, BigDecimal> values, MapSerializer serializer) {
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    PreludeSchemas.STRING,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    StringBigDecimalMapValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class StringBigDecimalMapValueSerializer implements BiConsumer<BigDecimal, ShapeSerializer> {
        private static final StringBigDecimalMapValueSerializer INSTANCE = new StringBigDecimalMapValueSerializer();

        @Override
        public void accept(BigDecimal values, ShapeSerializer serializer) {

            serializer.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, values);
        }
    }

    static Map<String, BigDecimal> deserializeStringBigDecimalMap(Schema schema, ShapeDeserializer deserializer) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        deserializer.readStringMap(schema, result, StringBigDecimalMapValueDeserializer.INSTANCE);
        return result;
    }

    private static final class StringBigDecimalMapValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, BigDecimal>> {
        static final StringBigDecimalMapValueDeserializer INSTANCE = new StringBigDecimalMapValueDeserializer();

        @Override
        public void accept(Map<String, BigDecimal> state, String key, ShapeDeserializer deserializer) {

            state.put(key, deserializer.readBigDecimal(PreludeSchemas.BIG_DECIMAL));
        }
    }

    static final class SparseUnionsSerializer implements BiConsumer<List<NestedUnion>, ShapeSerializer> {
        static final SparseUnionsSerializer INSTANCE = new SparseUnionsSerializer();

        @Override
        public void accept(List<NestedUnion> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(NestedUnion.SCHEMA);
                    continue;
                }
                serializer.writeStruct(NestedUnion.SCHEMA, value);
            }
        }
    }

    static List<NestedUnion> deserializeSparseUnions(Schema schema, ShapeDeserializer deserializer) {
        List<NestedUnion> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseUnionsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseUnionsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<NestedUnion>> {
        static final SparseUnionsMemberDeserializer INSTANCE = new SparseUnionsMemberDeserializer();

        @Override
        public void accept(List<NestedUnion> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(NestedUnion.builder().deserialize(deserializer).build());
        }
    }

    static final class SparseTimestampsSerializer implements BiConsumer<List<Instant>, ShapeSerializer> {
        static final SparseTimestampsSerializer INSTANCE = new SparseTimestampsSerializer();

        @Override
        public void accept(List<Instant> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.TIMESTAMP);
                    continue;
                }
                serializer.writeTimestamp(PreludeSchemas.TIMESTAMP, value);
            }
        }
    }

    static List<Instant> deserializeSparseTimestamps(Schema schema, ShapeDeserializer deserializer) {
        List<Instant> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseTimestampsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseTimestampsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Instant>> {
        static final SparseTimestampsMemberDeserializer INSTANCE = new SparseTimestampsMemberDeserializer();

        @Override
        public void accept(List<Instant> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readTimestamp(PreludeSchemas.TIMESTAMP));
        }
    }

    static final class SparseStructsSerializer implements BiConsumer<List<NestedStruct>, ShapeSerializer> {
        static final SparseStructsSerializer INSTANCE = new SparseStructsSerializer();

        @Override
        public void accept(List<NestedStruct> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(NestedStruct.SCHEMA);
                    continue;
                }
                serializer.writeStruct(NestedStruct.SCHEMA, value);
            }
        }
    }

    static List<NestedStruct> deserializeSparseStructs(Schema schema, ShapeDeserializer deserializer) {
        List<NestedStruct> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseStructsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStructsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<NestedStruct>> {
        static final SparseStructsMemberDeserializer INSTANCE = new SparseStructsMemberDeserializer();

        @Override
        public void accept(List<NestedStruct> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(NestedStruct.builder().deserialize(deserializer).build());
        }
    }

    static final class SparseStringsSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        static final SparseStringsSerializer INSTANCE = new SparseStringsSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.STRING);
                    continue;
                }
                serializer.writeString(PreludeSchemas.STRING, value);
            }
        }
    }

    static List<String> deserializeSparseStrings(Schema schema, ShapeDeserializer deserializer) {
        List<String> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseStringsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseStringsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<String>> {
        static final SparseStringsMemberDeserializer INSTANCE = new SparseStringsMemberDeserializer();

        @Override
        public void accept(List<String> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readString(PreludeSchemas.STRING));
        }
    }

    static final class SparseShortsSerializer implements BiConsumer<List<Short>, ShapeSerializer> {
        static final SparseShortsSerializer INSTANCE = new SparseShortsSerializer();

        @Override
        public void accept(List<Short> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.SHORT);
                    continue;
                }
                serializer.writeShort(PreludeSchemas.SHORT, value);
            }
        }
    }

    static List<Short> deserializeSparseShorts(Schema schema, ShapeDeserializer deserializer) {
        List<Short> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseShortsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseShortsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Short>> {
        static final SparseShortsMemberDeserializer INSTANCE = new SparseShortsMemberDeserializer();

        @Override
        public void accept(List<Short> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readShort(PreludeSchemas.SHORT));
        }
    }

    static final class SparseLongsSerializer implements BiConsumer<List<Long>, ShapeSerializer> {
        static final SparseLongsSerializer INSTANCE = new SparseLongsSerializer();

        @Override
        public void accept(List<Long> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.LONG);
                    continue;
                }
                serializer.writeLong(PreludeSchemas.LONG, value);
            }
        }
    }

    static List<Long> deserializeSparseLongs(Schema schema, ShapeDeserializer deserializer) {
        List<Long> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseLongsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseLongsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Long>> {
        static final SparseLongsMemberDeserializer INSTANCE = new SparseLongsMemberDeserializer();

        @Override
        public void accept(List<Long> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readLong(PreludeSchemas.LONG));
        }
    }

    static final class SparseIntEnumsSerializer implements BiConsumer<List<NestedIntEnum>, ShapeSerializer> {
        static final SparseIntEnumsSerializer INSTANCE = new SparseIntEnumsSerializer();

        @Override
        public void accept(List<NestedIntEnum> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(NestedIntEnum.SCHEMA);
                    continue;
                }
                serializer.writeInteger(NestedIntEnum.SCHEMA, value.value());
            }
        }
    }

    static List<NestedIntEnum> deserializeSparseIntEnums(Schema schema, ShapeDeserializer deserializer) {
        List<NestedIntEnum> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseIntEnumsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseIntEnumsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<NestedIntEnum>> {
        static final SparseIntEnumsMemberDeserializer INSTANCE = new SparseIntEnumsMemberDeserializer();

        @Override
        public void accept(List<NestedIntEnum> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(NestedIntEnum.builder().deserialize(deserializer).build());
        }
    }

    static final class SparseIntegersSerializer implements BiConsumer<List<Integer>, ShapeSerializer> {
        static final SparseIntegersSerializer INSTANCE = new SparseIntegersSerializer();

        @Override
        public void accept(List<Integer> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.INTEGER);
                    continue;
                }
                serializer.writeInteger(PreludeSchemas.INTEGER, value);
            }
        }
    }

    static List<Integer> deserializeSparseIntegers(Schema schema, ShapeDeserializer deserializer) {
        List<Integer> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseIntegersMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseIntegersMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Integer>> {
        static final SparseIntegersMemberDeserializer INSTANCE = new SparseIntegersMemberDeserializer();

        @Override
        public void accept(List<Integer> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readInteger(PreludeSchemas.INTEGER));
        }
    }

    static final class SparseFloatsSerializer implements BiConsumer<List<Float>, ShapeSerializer> {
        static final SparseFloatsSerializer INSTANCE = new SparseFloatsSerializer();

        @Override
        public void accept(List<Float> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.FLOAT);
                    continue;
                }
                serializer.writeFloat(PreludeSchemas.FLOAT, value);
            }
        }
    }

    static List<Float> deserializeSparseFloats(Schema schema, ShapeDeserializer deserializer) {
        List<Float> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseFloatsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseFloatsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Float>> {
        static final SparseFloatsMemberDeserializer INSTANCE = new SparseFloatsMemberDeserializer();

        @Override
        public void accept(List<Float> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readFloat(PreludeSchemas.FLOAT));
        }
    }

    static final class SparseEnumsSerializer implements BiConsumer<List<NestedEnum>, ShapeSerializer> {
        static final SparseEnumsSerializer INSTANCE = new SparseEnumsSerializer();

        @Override
        public void accept(List<NestedEnum> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(NestedEnum.SCHEMA);
                    continue;
                }
                serializer.writeString(NestedEnum.SCHEMA, value.value());
            }
        }
    }

    static List<NestedEnum> deserializeSparseEnums(Schema schema, ShapeDeserializer deserializer) {
        List<NestedEnum> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseEnumsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseEnumsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<NestedEnum>> {
        static final SparseEnumsMemberDeserializer INSTANCE = new SparseEnumsMemberDeserializer();

        @Override
        public void accept(List<NestedEnum> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(NestedEnum.builder().deserialize(deserializer).build());
        }
    }

    static final class SparseDoublesSerializer implements BiConsumer<List<Double>, ShapeSerializer> {
        static final SparseDoublesSerializer INSTANCE = new SparseDoublesSerializer();

        @Override
        public void accept(List<Double> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.DOUBLE);
                    continue;
                }
                serializer.writeDouble(PreludeSchemas.DOUBLE, value);
            }
        }
    }

    static List<Double> deserializeSparseDoubles(Schema schema, ShapeDeserializer deserializer) {
        List<Double> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseDoublesMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseDoublesMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Double>> {
        static final SparseDoublesMemberDeserializer INSTANCE = new SparseDoublesMemberDeserializer();

        @Override
        public void accept(List<Double> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readDouble(PreludeSchemas.DOUBLE));
        }
    }

    static final class SparseDocsSerializer implements BiConsumer<List<Document>, ShapeSerializer> {
        static final SparseDocsSerializer INSTANCE = new SparseDocsSerializer();

        @Override
        public void accept(List<Document> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.DOCUMENT);
                    continue;
                }
                serializer.writeDocument(PreludeSchemas.DOCUMENT, value);
            }
        }
    }

    static List<Document> deserializeSparseDocs(Schema schema, ShapeDeserializer deserializer) {
        List<Document> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseDocsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseDocsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Document>> {
        static final SparseDocsMemberDeserializer INSTANCE = new SparseDocsMemberDeserializer();

        @Override
        public void accept(List<Document> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readDocument());
        }
    }

    static final class SparseBytesSerializer implements BiConsumer<List<Byte>, ShapeSerializer> {
        static final SparseBytesSerializer INSTANCE = new SparseBytesSerializer();

        @Override
        public void accept(List<Byte> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.BYTE);
                    continue;
                }
                serializer.writeByte(PreludeSchemas.BYTE, value);
            }
        }
    }

    static List<Byte> deserializeSparseBytes(Schema schema, ShapeDeserializer deserializer) {
        List<Byte> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseBytesMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseBytesMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Byte>> {
        static final SparseBytesMemberDeserializer INSTANCE = new SparseBytesMemberDeserializer();

        @Override
        public void accept(List<Byte> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readByte(PreludeSchemas.BYTE));
        }
    }

    static final class SparseBooleansSerializer implements BiConsumer<List<Boolean>, ShapeSerializer> {
        static final SparseBooleansSerializer INSTANCE = new SparseBooleansSerializer();

        @Override
        public void accept(List<Boolean> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.BOOLEAN);
                    continue;
                }
                serializer.writeBoolean(PreludeSchemas.BOOLEAN, value);
            }
        }
    }

    static List<Boolean> deserializeSparseBooleans(Schema schema, ShapeDeserializer deserializer) {
        List<Boolean> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseBooleansMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseBooleansMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Boolean>> {
        static final SparseBooleansMemberDeserializer INSTANCE = new SparseBooleansMemberDeserializer();

        @Override
        public void accept(List<Boolean> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readBoolean(PreludeSchemas.BOOLEAN));
        }
    }

    static final class SparseBlobsSerializer implements BiConsumer<List<ByteBuffer>, ShapeSerializer> {
        static final SparseBlobsSerializer INSTANCE = new SparseBlobsSerializer();

        @Override
        public void accept(List<ByteBuffer> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.BLOB);
                    continue;
                }
                serializer.writeBlob(PreludeSchemas.BLOB, value);
            }
        }
    }

    static List<ByteBuffer> deserializeSparseBlobs(Schema schema, ShapeDeserializer deserializer) {
        List<ByteBuffer> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseBlobsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseBlobsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<ByteBuffer>> {
        static final SparseBlobsMemberDeserializer INSTANCE = new SparseBlobsMemberDeserializer();

        @Override
        public void accept(List<ByteBuffer> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readBlob(PreludeSchemas.BLOB));
        }
    }

    static final class SparseBigIntegersSerializer implements BiConsumer<List<BigInteger>, ShapeSerializer> {
        static final SparseBigIntegersSerializer INSTANCE = new SparseBigIntegersSerializer();

        @Override
        public void accept(List<BigInteger> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.BIG_INTEGER);
                    continue;
                }
                serializer.writeBigInteger(PreludeSchemas.BIG_INTEGER, value);
            }
        }
    }

    static List<BigInteger> deserializeSparseBigIntegers(Schema schema, ShapeDeserializer deserializer) {
        List<BigInteger> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseBigIntegersMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseBigIntegersMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<BigInteger>> {
        static final SparseBigIntegersMemberDeserializer INSTANCE = new SparseBigIntegersMemberDeserializer();

        @Override
        public void accept(List<BigInteger> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readBigInteger(PreludeSchemas.BIG_INTEGER));
        }
    }

    static final class SparseBigDecimalsSerializer implements BiConsumer<List<BigDecimal>, ShapeSerializer> {
        static final SparseBigDecimalsSerializer INSTANCE = new SparseBigDecimalsSerializer();

        @Override
        public void accept(List<BigDecimal> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(PreludeSchemas.BIG_DECIMAL);
                    continue;
                }
                serializer.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, value);
            }
        }
    }

    static List<BigDecimal> deserializeSparseBigDecimals(Schema schema, ShapeDeserializer deserializer) {
        List<BigDecimal> result = new ArrayList<>();
        deserializer.readList(schema, result, SparseBigDecimalsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseBigDecimalsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<BigDecimal>> {
        static final SparseBigDecimalsMemberDeserializer INSTANCE = new SparseBigDecimalsMemberDeserializer();

        @Override
        public void accept(List<BigDecimal> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readBigDecimal(PreludeSchemas.BIG_DECIMAL));
        }
    }

    static final class SetOfUnionsSerializer implements BiConsumer<Set<NestedUnion>, ShapeSerializer> {
        static final SetOfUnionsSerializer INSTANCE = new SetOfUnionsSerializer();

        @Override
        public void accept(Set<NestedUnion> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeStruct(NestedUnion.SCHEMA, value);
            }
        }
    }

    static Set<NestedUnion> deserializeSetOfUnions(Schema schema, ShapeDeserializer deserializer) {
        Set<NestedUnion> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfUnionsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfUnionsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<NestedUnion>> {
        static final SetOfUnionsMemberDeserializer INSTANCE = new SetOfUnionsMemberDeserializer();

        @Override
        public void accept(Set<NestedUnion> state, ShapeDeserializer deserializer) {

            if (!state.add(NestedUnion.builder().deserialize(deserializer).build())) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class SetOfTimestampsSerializer implements BiConsumer<Set<Instant>, ShapeSerializer> {
        static final SetOfTimestampsSerializer INSTANCE = new SetOfTimestampsSerializer();

        @Override
        public void accept(Set<Instant> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeTimestamp(PreludeSchemas.TIMESTAMP, value);
            }
        }
    }

    static Set<Instant> deserializeSetOfTimestamps(Schema schema, ShapeDeserializer deserializer) {
        Set<Instant> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfTimestampsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfTimestampsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<Instant>> {
        static final SetOfTimestampsMemberDeserializer INSTANCE = new SetOfTimestampsMemberDeserializer();

        @Override
        public void accept(Set<Instant> state, ShapeDeserializer deserializer) {

            if (!state.add(deserializer.readTimestamp(PreludeSchemas.TIMESTAMP))) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class SetOfStructsSerializer implements BiConsumer<Set<NestedStruct>, ShapeSerializer> {
        static final SetOfStructsSerializer INSTANCE = new SetOfStructsSerializer();

        @Override
        public void accept(Set<NestedStruct> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeStruct(NestedStruct.SCHEMA, value);
            }
        }
    }

    static Set<NestedStruct> deserializeSetOfStructs(Schema schema, ShapeDeserializer deserializer) {
        Set<NestedStruct> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfStructsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfStructsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<NestedStruct>> {
        static final SetOfStructsMemberDeserializer INSTANCE = new SetOfStructsMemberDeserializer();

        @Override
        public void accept(Set<NestedStruct> state, ShapeDeserializer deserializer) {

            if (!state.add(NestedStruct.builder().deserialize(deserializer).build())) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class SetOfStringMapSerializer implements BiConsumer<Set<Map<String, String>>, ShapeSerializer> {
        static final SetOfStringMapSerializer INSTANCE = new SetOfStringMapSerializer();

        @Override
        public void accept(Set<Map<String, String>> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeMap(SharedSchemas.STRING_STRING_MAP, value, SharedSerde.StringStringMapSerializer.INSTANCE);
            }
        }
    }

    static Set<Map<String, String>> deserializeSetOfStringMap(Schema schema, ShapeDeserializer deserializer) {
        Set<Map<String, String>> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfStringMapMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfStringMapMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<Map<String, String>>> {
        static final SetOfStringMapMemberDeserializer INSTANCE = new SetOfStringMapMemberDeserializer();

        @Override
        public void accept(Set<Map<String, String>> state, ShapeDeserializer deserializer) {

            if (!state.add(SharedSerde.deserializeStringStringMap(SharedSchemas.STRING_STRING_MAP, deserializer))) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class SetOfStringListSerializer implements BiConsumer<Set<List<String>>, ShapeSerializer> {
        static final SetOfStringListSerializer INSTANCE = new SetOfStringListSerializer();

        @Override
        public void accept(Set<List<String>> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeList(SharedSchemas.LIST_OF_STRING, value, SharedSerde.ListOfStringSerializer.INSTANCE);
            }
        }
    }

    static Set<List<String>> deserializeSetOfStringList(Schema schema, ShapeDeserializer deserializer) {
        Set<List<String>> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfStringListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfStringListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<List<String>>> {
        static final SetOfStringListMemberDeserializer INSTANCE = new SetOfStringListMemberDeserializer();

        @Override
        public void accept(Set<List<String>> state, ShapeDeserializer deserializer) {

            if (!state.add(SharedSerde.deserializeListOfString(SharedSchemas.LIST_OF_STRING, deserializer))) {
                throw new SerializationException("Member must have unique values");
            }
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

    static Set<String> deserializeSetOfStrings(Schema schema, ShapeDeserializer deserializer) {
        Set<String> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfStringsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfStringsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<String>> {
        static final SetOfStringsMemberDeserializer INSTANCE = new SetOfStringsMemberDeserializer();

        @Override
        public void accept(Set<String> state, ShapeDeserializer deserializer) {

            if (!state.add(deserializer.readString(PreludeSchemas.STRING))) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class SetOfNumberSerializer implements BiConsumer<Set<Integer>, ShapeSerializer> {
        static final SetOfNumberSerializer INSTANCE = new SetOfNumberSerializer();

        @Override
        public void accept(Set<Integer> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeInteger(PreludeSchemas.INTEGER, value);
            }
        }
    }

    static Set<Integer> deserializeSetOfNumber(Schema schema, ShapeDeserializer deserializer) {
        Set<Integer> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfNumberMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfNumberMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<Integer>> {
        static final SetOfNumberMemberDeserializer INSTANCE = new SetOfNumberMemberDeserializer();

        @Override
        public void accept(Set<Integer> state, ShapeDeserializer deserializer) {

            if (!state.add(deserializer.readInteger(PreludeSchemas.INTEGER))) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class SetOfIntEnumsSerializer implements BiConsumer<Set<NestedIntEnum>, ShapeSerializer> {
        static final SetOfIntEnumsSerializer INSTANCE = new SetOfIntEnumsSerializer();

        @Override
        public void accept(Set<NestedIntEnum> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeInteger(NestedIntEnum.SCHEMA, value.value());
            }
        }
    }

    static Set<NestedIntEnum> deserializeSetOfIntEnums(Schema schema, ShapeDeserializer deserializer) {
        Set<NestedIntEnum> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfIntEnumsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfIntEnumsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<NestedIntEnum>> {
        static final SetOfIntEnumsMemberDeserializer INSTANCE = new SetOfIntEnumsMemberDeserializer();

        @Override
        public void accept(Set<NestedIntEnum> state, ShapeDeserializer deserializer) {

            if (!state.add(NestedIntEnum.builder().deserialize(deserializer).build())) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class SetOfEnumsSerializer implements BiConsumer<Set<NestedEnum>, ShapeSerializer> {
        static final SetOfEnumsSerializer INSTANCE = new SetOfEnumsSerializer();

        @Override
        public void accept(Set<NestedEnum> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeString(NestedEnum.SCHEMA, value.value());
            }
        }
    }

    static Set<NestedEnum> deserializeSetOfEnums(Schema schema, ShapeDeserializer deserializer) {
        Set<NestedEnum> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfEnumsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfEnumsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<NestedEnum>> {
        static final SetOfEnumsMemberDeserializer INSTANCE = new SetOfEnumsMemberDeserializer();

        @Override
        public void accept(Set<NestedEnum> state, ShapeDeserializer deserializer) {

            if (!state.add(NestedEnum.builder().deserialize(deserializer).build())) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class SetOfBooleansSerializer implements BiConsumer<Set<Boolean>, ShapeSerializer> {
        static final SetOfBooleansSerializer INSTANCE = new SetOfBooleansSerializer();

        @Override
        public void accept(Set<Boolean> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeBoolean(PreludeSchemas.BOOLEAN, value);
            }
        }
    }

    static Set<Boolean> deserializeSetOfBooleans(Schema schema, ShapeDeserializer deserializer) {
        Set<Boolean> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfBooleansMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfBooleansMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<Boolean>> {
        static final SetOfBooleansMemberDeserializer INSTANCE = new SetOfBooleansMemberDeserializer();

        @Override
        public void accept(Set<Boolean> state, ShapeDeserializer deserializer) {

            if (!state.add(deserializer.readBoolean(PreludeSchemas.BOOLEAN))) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class SetOfBlobsSerializer implements BiConsumer<Set<ByteBuffer>, ShapeSerializer> {
        static final SetOfBlobsSerializer INSTANCE = new SetOfBlobsSerializer();

        @Override
        public void accept(Set<ByteBuffer> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeBlob(PreludeSchemas.BLOB, value);
            }
        }
    }

    static Set<ByteBuffer> deserializeSetOfBlobs(Schema schema, ShapeDeserializer deserializer) {
        Set<ByteBuffer> result = new LinkedHashSet<>();
        deserializer.readList(schema, result, SetOfBlobsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SetOfBlobsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<Set<ByteBuffer>> {
        static final SetOfBlobsMemberDeserializer INSTANCE = new SetOfBlobsMemberDeserializer();

        @Override
        public void accept(Set<ByteBuffer> state, ShapeDeserializer deserializer) {

            if (!state.add(deserializer.readBlob(PreludeSchemas.BLOB))) {
                throw new SerializationException("Member must have unique values");
            }
        }
    }

    static final class ListOfMapsSerializer implements BiConsumer<List<Map<String, String>>, ShapeSerializer> {
        static final ListOfMapsSerializer INSTANCE = new ListOfMapsSerializer();

        @Override
        public void accept(List<Map<String, String>> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeMap(SharedSchemas.STRING_STRING_MAP, value, SharedSerde.StringStringMapSerializer.INSTANCE);
            }
        }
    }

    static List<Map<String, String>> deserializeListOfMaps(Schema schema, ShapeDeserializer deserializer) {
        List<Map<String, String>> result = new ArrayList<>();
        deserializer.readList(schema, result, ListOfMapsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ListOfMapsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Map<String, String>>> {
        static final ListOfMapsMemberDeserializer INSTANCE = new ListOfMapsMemberDeserializer();

        @Override
        public void accept(List<Map<String, String>> state, ShapeDeserializer deserializer) {

            state.add(SharedSerde.deserializeStringStringMap(SharedSchemas.STRING_STRING_MAP, deserializer));
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
                    StringStringMapValueSerializer.INSTANCE
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

    static Map<String, String> deserializeStringStringMap(Schema schema, ShapeDeserializer deserializer) {
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

    static final class ListOfListOfStringListSerializer implements BiConsumer<List<List<List<String>>>, ShapeSerializer> {
        static final ListOfListOfStringListSerializer INSTANCE = new ListOfListOfStringListSerializer();

        @Override
        public void accept(List<List<List<String>>> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeList(SharedSchemas.LIST_OF_STRING_LIST, value, SharedSerde.ListOfStringListSerializer.INSTANCE);
            }
        }
    }

    static List<List<List<String>>> deserializeListOfListOfStringList(Schema schema, ShapeDeserializer deserializer) {
        List<List<List<String>>> result = new ArrayList<>();
        deserializer.readList(schema, result, ListOfListOfStringListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ListOfListOfStringListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<List<List<String>>>> {
        static final ListOfListOfStringListMemberDeserializer INSTANCE = new ListOfListOfStringListMemberDeserializer();

        @Override
        public void accept(List<List<List<String>>> state, ShapeDeserializer deserializer) {

            state.add(SharedSerde.deserializeListOfStringList(SharedSchemas.LIST_OF_STRING_LIST, deserializer));
        }
    }

    static final class ListOfStringListSerializer implements BiConsumer<List<List<String>>, ShapeSerializer> {
        static final ListOfStringListSerializer INSTANCE = new ListOfStringListSerializer();

        @Override
        public void accept(List<List<String>> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeList(SharedSchemas.LIST_OF_STRING, value, SharedSerde.ListOfStringSerializer.INSTANCE);
            }
        }
    }

    static List<List<String>> deserializeListOfStringList(Schema schema, ShapeDeserializer deserializer) {
        List<List<String>> result = new ArrayList<>();
        deserializer.readList(schema, result, ListOfStringListMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ListOfStringListMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<List<String>>> {
        static final ListOfStringListMemberDeserializer INSTANCE = new ListOfStringListMemberDeserializer();

        @Override
        public void accept(List<List<String>> state, ShapeDeserializer deserializer) {

            state.add(SharedSerde.deserializeListOfString(SharedSchemas.LIST_OF_STRING, deserializer));
        }
    }

    static final class UnionsSerializer implements BiConsumer<List<NestedUnion>, ShapeSerializer> {
        static final UnionsSerializer INSTANCE = new UnionsSerializer();

        @Override
        public void accept(List<NestedUnion> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeStruct(NestedUnion.SCHEMA, value);
            }
        }
    }

    static List<NestedUnion> deserializeUnions(Schema schema, ShapeDeserializer deserializer) {
        List<NestedUnion> result = new ArrayList<>();
        deserializer.readList(schema, result, UnionsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class UnionsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<NestedUnion>> {
        static final UnionsMemberDeserializer INSTANCE = new UnionsMemberDeserializer();

        @Override
        public void accept(List<NestedUnion> state, ShapeDeserializer deserializer) {

            state.add(NestedUnion.builder().deserialize(deserializer).build());
        }
    }

    static final class TimestampsSerializer implements BiConsumer<List<Instant>, ShapeSerializer> {
        static final TimestampsSerializer INSTANCE = new TimestampsSerializer();

        @Override
        public void accept(List<Instant> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeTimestamp(PreludeSchemas.TIMESTAMP, value);
            }
        }
    }

    static List<Instant> deserializeTimestamps(Schema schema, ShapeDeserializer deserializer) {
        List<Instant> result = new ArrayList<>();
        deserializer.readList(schema, result, TimestampsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class TimestampsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Instant>> {
        static final TimestampsMemberDeserializer INSTANCE = new TimestampsMemberDeserializer();

        @Override
        public void accept(List<Instant> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readTimestamp(PreludeSchemas.TIMESTAMP));
        }
    }

    static final class StructsSerializer implements BiConsumer<List<NestedStruct>, ShapeSerializer> {
        static final StructsSerializer INSTANCE = new StructsSerializer();

        @Override
        public void accept(List<NestedStruct> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeStruct(NestedStruct.SCHEMA, value);
            }
        }
    }

    static List<NestedStruct> deserializeStructs(Schema schema, ShapeDeserializer deserializer) {
        List<NestedStruct> result = new ArrayList<>();
        deserializer.readList(schema, result, StructsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class StructsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<NestedStruct>> {
        static final StructsMemberDeserializer INSTANCE = new StructsMemberDeserializer();

        @Override
        public void accept(List<NestedStruct> state, ShapeDeserializer deserializer) {

            state.add(NestedStruct.builder().deserialize(deserializer).build());
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

    static final class ShortsSerializer implements BiConsumer<List<Short>, ShapeSerializer> {
        static final ShortsSerializer INSTANCE = new ShortsSerializer();

        @Override
        public void accept(List<Short> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeShort(PreludeSchemas.SHORT, value);
            }
        }
    }

    static List<Short> deserializeShorts(Schema schema, ShapeDeserializer deserializer) {
        List<Short> result = new ArrayList<>();
        deserializer.readList(schema, result, ShortsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class ShortsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Short>> {
        static final ShortsMemberDeserializer INSTANCE = new ShortsMemberDeserializer();

        @Override
        public void accept(List<Short> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readShort(PreludeSchemas.SHORT));
        }
    }

    static final class LongsSerializer implements BiConsumer<List<Long>, ShapeSerializer> {
        static final LongsSerializer INSTANCE = new LongsSerializer();

        @Override
        public void accept(List<Long> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeLong(PreludeSchemas.LONG, value);
            }
        }
    }

    static List<Long> deserializeLongs(Schema schema, ShapeDeserializer deserializer) {
        List<Long> result = new ArrayList<>();
        deserializer.readList(schema, result, LongsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class LongsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Long>> {
        static final LongsMemberDeserializer INSTANCE = new LongsMemberDeserializer();

        @Override
        public void accept(List<Long> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readLong(PreludeSchemas.LONG));
        }
    }

    static final class IntEnumsSerializer implements BiConsumer<List<NestedIntEnum>, ShapeSerializer> {
        static final IntEnumsSerializer INSTANCE = new IntEnumsSerializer();

        @Override
        public void accept(List<NestedIntEnum> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeInteger(NestedIntEnum.SCHEMA, value.value());
            }
        }
    }

    static List<NestedIntEnum> deserializeIntEnums(Schema schema, ShapeDeserializer deserializer) {
        List<NestedIntEnum> result = new ArrayList<>();
        deserializer.readList(schema, result, IntEnumsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class IntEnumsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<NestedIntEnum>> {
        static final IntEnumsMemberDeserializer INSTANCE = new IntEnumsMemberDeserializer();

        @Override
        public void accept(List<NestedIntEnum> state, ShapeDeserializer deserializer) {

            state.add(NestedIntEnum.builder().deserialize(deserializer).build());
        }
    }

    static final class IntegersSerializer implements BiConsumer<List<Integer>, ShapeSerializer> {
        static final IntegersSerializer INSTANCE = new IntegersSerializer();

        @Override
        public void accept(List<Integer> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeInteger(PreludeSchemas.INTEGER, value);
            }
        }
    }

    static List<Integer> deserializeIntegers(Schema schema, ShapeDeserializer deserializer) {
        List<Integer> result = new ArrayList<>();
        deserializer.readList(schema, result, IntegersMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class IntegersMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Integer>> {
        static final IntegersMemberDeserializer INSTANCE = new IntegersMemberDeserializer();

        @Override
        public void accept(List<Integer> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readInteger(PreludeSchemas.INTEGER));
        }
    }

    static final class FloatsSerializer implements BiConsumer<List<Float>, ShapeSerializer> {
        static final FloatsSerializer INSTANCE = new FloatsSerializer();

        @Override
        public void accept(List<Float> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeFloat(PreludeSchemas.FLOAT, value);
            }
        }
    }

    static List<Float> deserializeFloats(Schema schema, ShapeDeserializer deserializer) {
        List<Float> result = new ArrayList<>();
        deserializer.readList(schema, result, FloatsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class FloatsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Float>> {
        static final FloatsMemberDeserializer INSTANCE = new FloatsMemberDeserializer();

        @Override
        public void accept(List<Float> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readFloat(PreludeSchemas.FLOAT));
        }
    }

    static final class EnumsSerializer implements BiConsumer<List<NestedEnum>, ShapeSerializer> {
        static final EnumsSerializer INSTANCE = new EnumsSerializer();

        @Override
        public void accept(List<NestedEnum> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeString(NestedEnum.SCHEMA, value.value());
            }
        }
    }

    static List<NestedEnum> deserializeEnums(Schema schema, ShapeDeserializer deserializer) {
        List<NestedEnum> result = new ArrayList<>();
        deserializer.readList(schema, result, EnumsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class EnumsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<NestedEnum>> {
        static final EnumsMemberDeserializer INSTANCE = new EnumsMemberDeserializer();

        @Override
        public void accept(List<NestedEnum> state, ShapeDeserializer deserializer) {

            state.add(NestedEnum.builder().deserialize(deserializer).build());
        }
    }

    static final class DoublesSerializer implements BiConsumer<List<Double>, ShapeSerializer> {
        static final DoublesSerializer INSTANCE = new DoublesSerializer();

        @Override
        public void accept(List<Double> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeDouble(PreludeSchemas.DOUBLE, value);
            }
        }
    }

    static List<Double> deserializeDoubles(Schema schema, ShapeDeserializer deserializer) {
        List<Double> result = new ArrayList<>();
        deserializer.readList(schema, result, DoublesMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class DoublesMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Double>> {
        static final DoublesMemberDeserializer INSTANCE = new DoublesMemberDeserializer();

        @Override
        public void accept(List<Double> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readDouble(PreludeSchemas.DOUBLE));
        }
    }

    static final class DocsSerializer implements BiConsumer<List<Document>, ShapeSerializer> {
        static final DocsSerializer INSTANCE = new DocsSerializer();

        @Override
        public void accept(List<Document> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeDocument(PreludeSchemas.DOCUMENT, value);
            }
        }
    }

    static List<Document> deserializeDocs(Schema schema, ShapeDeserializer deserializer) {
        List<Document> result = new ArrayList<>();
        deserializer.readList(schema, result, DocsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class DocsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Document>> {
        static final DocsMemberDeserializer INSTANCE = new DocsMemberDeserializer();

        @Override
        public void accept(List<Document> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readDocument());
        }
    }

    static final class BytesSerializer implements BiConsumer<List<Byte>, ShapeSerializer> {
        static final BytesSerializer INSTANCE = new BytesSerializer();

        @Override
        public void accept(List<Byte> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeByte(PreludeSchemas.BYTE, value);
            }
        }
    }

    static List<Byte> deserializeBytes(Schema schema, ShapeDeserializer deserializer) {
        List<Byte> result = new ArrayList<>();
        deserializer.readList(schema, result, BytesMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class BytesMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Byte>> {
        static final BytesMemberDeserializer INSTANCE = new BytesMemberDeserializer();

        @Override
        public void accept(List<Byte> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readByte(PreludeSchemas.BYTE));
        }
    }

    static final class BooleansSerializer implements BiConsumer<List<Boolean>, ShapeSerializer> {
        static final BooleansSerializer INSTANCE = new BooleansSerializer();

        @Override
        public void accept(List<Boolean> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeBoolean(PreludeSchemas.BOOLEAN, value);
            }
        }
    }

    static List<Boolean> deserializeBooleans(Schema schema, ShapeDeserializer deserializer) {
        List<Boolean> result = new ArrayList<>();
        deserializer.readList(schema, result, BooleansMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class BooleansMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<Boolean>> {
        static final BooleansMemberDeserializer INSTANCE = new BooleansMemberDeserializer();

        @Override
        public void accept(List<Boolean> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readBoolean(PreludeSchemas.BOOLEAN));
        }
    }

    static final class BlobsSerializer implements BiConsumer<List<ByteBuffer>, ShapeSerializer> {
        static final BlobsSerializer INSTANCE = new BlobsSerializer();

        @Override
        public void accept(List<ByteBuffer> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeBlob(PreludeSchemas.BLOB, value);
            }
        }
    }

    static List<ByteBuffer> deserializeBlobs(Schema schema, ShapeDeserializer deserializer) {
        List<ByteBuffer> result = new ArrayList<>();
        deserializer.readList(schema, result, BlobsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class BlobsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<ByteBuffer>> {
        static final BlobsMemberDeserializer INSTANCE = new BlobsMemberDeserializer();

        @Override
        public void accept(List<ByteBuffer> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readBlob(PreludeSchemas.BLOB));
        }
    }

    static final class BigIntegersSerializer implements BiConsumer<List<BigInteger>, ShapeSerializer> {
        static final BigIntegersSerializer INSTANCE = new BigIntegersSerializer();

        @Override
        public void accept(List<BigInteger> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeBigInteger(PreludeSchemas.BIG_INTEGER, value);
            }
        }
    }

    static List<BigInteger> deserializeBigIntegers(Schema schema, ShapeDeserializer deserializer) {
        List<BigInteger> result = new ArrayList<>();
        deserializer.readList(schema, result, BigIntegersMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class BigIntegersMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<BigInteger>> {
        static final BigIntegersMemberDeserializer INSTANCE = new BigIntegersMemberDeserializer();

        @Override
        public void accept(List<BigInteger> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readBigInteger(PreludeSchemas.BIG_INTEGER));
        }
    }

    static final class BigDecimalsSerializer implements BiConsumer<List<BigDecimal>, ShapeSerializer> {
        static final BigDecimalsSerializer INSTANCE = new BigDecimalsSerializer();

        @Override
        public void accept(List<BigDecimal> values, ShapeSerializer serializer) {
            for (var value : values) {

                serializer.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, value);
            }
        }
    }

    static List<BigDecimal> deserializeBigDecimals(Schema schema, ShapeDeserializer deserializer) {
        List<BigDecimal> result = new ArrayList<>();
        deserializer.readList(schema, result, BigDecimalsMemberDeserializer.INSTANCE);
        return result;
    }

    private static final class BigDecimalsMemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<BigDecimal>> {
        static final BigDecimalsMemberDeserializer INSTANCE = new BigDecimalsMemberDeserializer();

        @Override
        public void accept(List<BigDecimal> state, ShapeDeserializer deserializer) {

            state.add(deserializer.readBigDecimal(PreludeSchemas.BIG_DECIMAL));
        }
    }

    private SharedSerde() {}
}

