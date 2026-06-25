package software.amazon.smithy.java.example.standalone.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;


/**
 * Defines shared serialization and deserialization methods for map and list shapes.
 */
final class SharedSerde {

    static final class SparseMapSerializer implements BiConsumer<Map<String, String>, MapSerializer> {
        static final SparseMapSerializer INSTANCE = new SparseMapSerializer();

        @Override
        public void accept(Map<String, String> values, MapSerializer serializer) {
            var $k = Schemas.SPARSE_MAP.mapKeyMember();
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    $k,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    SparseMap$ValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class SparseMap$ValueSerializer implements BiConsumer<String, ShapeSerializer> {
        private static final SparseMap$ValueSerializer INSTANCE = new SparseMap$ValueSerializer();

        @Override
        public void accept(String values, ShapeSerializer serializer) {
            if (values == null) {
                serializer.writeNull(Schemas.SPARSE_MAP.mapValueMember());
                return;
            }
            serializer.writeString(Schemas.SPARSE_MAP.mapValueMember(), values);
        }
    }

    static Map<String, String> deserializeSparseMap(Schema schema, ShapeDeserializer deserializer) {
        var size = Math.min(deserializer.containerSize(), deserializer.containerPreAllocationLimit());
        Map<String, String> result = size == -1 ? new LinkedHashMap<>() : LinkedHashMap.newLinkedHashMap(size);
        deserializer.readStringMap(schema, result, SparseMap$ValueDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseMap$ValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, String>> {
        static final SparseMap$ValueDeserializer INSTANCE = new SparseMap$ValueDeserializer();

        @Override
        public void accept(Map<String, String> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.put(key, deserializer.readNull());
                return;
            }
            state.put(key, deserializer.readString(Schemas.SPARSE_MAP.mapValueMember()));
        }
    }

    static final class SparseListSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        static final SparseListSerializer INSTANCE = new SparseListSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {
            var $m = Schemas.SPARSE_LIST.listMember();
            if (values instanceof RandomAccess) {
                for (int i = 0, size = values.size(); i < size; i++) {
                    var value = values.get(i);
                    if (value == null) {
                        serializer.writeNull($m);
                        continue;
                    }
                    serializer.writeString($m, value);
                }
            } else {
                for (var value : values) {
                    if (value == null) {
                        serializer.writeNull($m);
                        continue;
                    }
                    serializer.writeString($m, value);
                }
            }
        }
    }

    static List<String> deserializeSparseList(Schema schema, ShapeDeserializer deserializer) {
        var size = Math.min(deserializer.containerSize(), deserializer.containerPreAllocationLimit());
        List<String> result = size == -1 ? new ArrayList<>() : new ArrayList<>(size);
        deserializer.readList(schema, result, SparseList$MemberDeserializer.INSTANCE);
        return result;
    }

    private static final class SparseList$MemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<String>> {
        static final SparseList$MemberDeserializer INSTANCE = new SparseList$MemberDeserializer();

        @Override
        public void accept(List<String> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                state.add(deserializer.readNull());
                return;
            }
            state.add(deserializer.readString(Schemas.SPARSE_LIST.listMember()));
        }
    }

    static final class DenseMapSerializer implements BiConsumer<Map<String, String>, MapSerializer> {
        static final DenseMapSerializer INSTANCE = new DenseMapSerializer();

        @Override
        public void accept(Map<String, String> values, MapSerializer serializer) {
            var $k = Schemas.DENSE_MAP.mapKeyMember();
            for (var valueEntry : values.entrySet()) {
                serializer.writeEntry(
                    $k,
                    valueEntry.getKey(),
                    valueEntry.getValue(),
                    DenseMap$ValueSerializer.INSTANCE
                );
            }
        }
    }

    private static final class DenseMap$ValueSerializer implements BiConsumer<String, ShapeSerializer> {
        private static final DenseMap$ValueSerializer INSTANCE = new DenseMap$ValueSerializer();

        @Override
        public void accept(String values, ShapeSerializer serializer) {
            serializer.writeString(Schemas.DENSE_MAP.mapValueMember(), values);
        }
    }

    static Map<String, String> deserializeDenseMap(Schema schema, ShapeDeserializer deserializer) {
        var size = Math.min(deserializer.containerSize(), deserializer.containerPreAllocationLimit());
        Map<String, String> result = size == -1 ? new LinkedHashMap<>() : LinkedHashMap.newLinkedHashMap(size);
        deserializer.readStringMap(schema, result, DenseMap$ValueDeserializer.INSTANCE);
        return result;
    }

    private static final class DenseMap$ValueDeserializer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, String>> {
        static final DenseMap$ValueDeserializer INSTANCE = new DenseMap$ValueDeserializer();

        @Override
        public void accept(Map<String, String> state, String key, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                throw new SerializationException("Null value found in dense map");
            }
            state.put(key, deserializer.readString(Schemas.DENSE_MAP.mapValueMember()));
        }
    }

    static final class DenseListSerializer implements BiConsumer<List<String>, ShapeSerializer> {
        static final DenseListSerializer INSTANCE = new DenseListSerializer();

        @Override
        public void accept(List<String> values, ShapeSerializer serializer) {
            var $m = Schemas.DENSE_LIST.listMember();
            if (values instanceof RandomAccess) {
                for (int i = 0, size = values.size(); i < size; i++) {
                    var value = values.get(i);
                    serializer.writeString($m, value);
                }
            } else {
                for (var value : values) {
                    serializer.writeString($m, value);
                }
            }
        }
    }

    static List<String> deserializeDenseList(Schema schema, ShapeDeserializer deserializer) {
        var size = Math.min(deserializer.containerSize(), deserializer.containerPreAllocationLimit());
        List<String> result = size == -1 ? new ArrayList<>() : new ArrayList<>(size);
        deserializer.readList(schema, result, DenseList$MemberDeserializer.INSTANCE);
        return result;
    }

    private static final class DenseList$MemberDeserializer implements ShapeDeserializer.ListMemberConsumer<List<String>> {
        static final DenseList$MemberDeserializer INSTANCE = new DenseList$MemberDeserializer();

        @Override
        public void accept(List<String> state, ShapeDeserializer deserializer) {
            if (deserializer.isNull()) {
                throw new SerializationException("Null value found in dense list");
            }
            state.add(deserializer.readString(Schemas.DENSE_LIST.listMember()));
        }
    }

    private SharedSerde() {}
}
