package software.amazon.smithy.java.example.standalone.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;


final class SharedSerde {

    static void deserializeSparseMapValue(
            Map<String, String> state,
            String key,
            ShapeDeserializer deserializer
    ) {
        if (deserializer.isNull()) {
            state.put(key, deserializer.readNull());
            return;
        }
        state.put(key, deserializer.readString(Schemas.SPARSE_MAP.mapValueMember()));
    }

    static Map<String, String> deserializeSparseMap(
            Schema schema,
            ShapeDeserializer deserializer
    ) {
        var size = Math.min(
                deserializer.containerSize(),
                deserializer.containerPreAllocationLimit());
        Map<String, String> result = size == -1
                ? new LinkedHashMap<>()
                : LinkedHashMap.newLinkedHashMap(size);
        deserializer.readStringMap(
                schema,
                result,
                SharedSerde::deserializeSparseMapValue);
        return result;
    }

    static void deserializeSparseListMember(
            List<String> state,
            ShapeDeserializer deserializer
    ) {
        if (deserializer.isNull()) {
            state.add(deserializer.readNull());
            return;
        }
        state.add(deserializer.readString(Schemas.SPARSE_LIST.listMember()));
    }

    static List<String> deserializeSparseList(
            Schema schema,
            ShapeDeserializer deserializer
    ) {
        var size = Math.min(
                deserializer.containerSize(),
                deserializer.containerPreAllocationLimit());
        List<String> result = size == -1
                ? new ArrayList<>()
                : new ArrayList<>(size);
        deserializer.readList(
                schema,
                result,
                SharedSerde::deserializeSparseListMember);
        return result;
    }

    static void deserializeDenseMapValue(
            Map<String, String> state,
            String key,
            ShapeDeserializer deserializer
    ) {
        if (deserializer.isNull()) {
            throw new SerializationException("Null value found in dense map");
        }
        state.put(key, deserializer.readString(Schemas.DENSE_MAP.mapValueMember()));
    }

    static Map<String, String> deserializeDenseMap(
            Schema schema,
            ShapeDeserializer deserializer
    ) {
        var size = Math.min(
                deserializer.containerSize(),
                deserializer.containerPreAllocationLimit());
        Map<String, String> result = size == -1
                ? new LinkedHashMap<>()
                : LinkedHashMap.newLinkedHashMap(size);
        deserializer.readStringMap(
                schema,
                result,
                SharedSerde::deserializeDenseMapValue);
        return result;
    }

    static void deserializeDenseListMember(
            List<String> state,
            ShapeDeserializer deserializer
    ) {
        if (deserializer.isNull()) {
            throw new SerializationException("Null value found in dense list");
        }
        state.add(deserializer.readString(Schemas.DENSE_LIST.listMember()));
    }

    static List<String> deserializeDenseList(
            Schema schema,
            ShapeDeserializer deserializer
    ) {
        var size = Math.min(
                deserializer.containerSize(),
                deserializer.containerPreAllocationLimit());
        List<String> result = size == -1
                ? new ArrayList<>()
                : new ArrayList<>(size);
        deserializer.readList(
                schema,
                result,
                SharedSerde::deserializeDenseListMember);
        return result;
    }

    private SharedSerde() {}
}
