/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.testing;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.SparseTrait;

public final class CollectionWriterFixture {

    private CollectionWriterFixture() {}

    public record Options(boolean documents, boolean maps) {
        public static final Options ALL = new Options(true, true);
        public static final Options NO_DOCUMENTS = new Options(false, true);
        public static final Options LISTS_ONLY = new Options(false, false);
    }

    private static ShapeId id(String name) {
        return ShapeId.from("smithy.java.test#" + name);
    }

    private static Schema list(String name, Schema member, boolean sparse) {
        var builder = sparse ? Schema.listBuilder(id(name), new SparseTrait()) : Schema.listBuilder(id(name));
        return builder.putMember("member", member).build();
    }

    private static Schema map(String name, Schema value, boolean sparse) {
        var builder = sparse ? Schema.mapBuilder(id(name), new SparseTrait()) : Schema.mapBuilder(id(name));
        return builder.putMember("key", PreludeSchemas.STRING).putMember("value", value).build();
    }

    public static final Schema COLOR = Schema.createEnum(id("Color"), Set.of("RED", "BLUE"));
    public static final Schema LEVEL = Schema.createIntEnum(id("Level"), Set.of(1, 2));
    public static final Schema ITEM = Schema.structureBuilder(id("Item"))
            .putMember("value", PreludeSchemas.STRING)
            .build();

    public static final Schema BOOLEAN_LIST = list("BooleanList", PreludeSchemas.BOOLEAN, false);
    public static final Schema SPARSE_BOOLEAN_LIST = list("SparseBooleanList", PreludeSchemas.BOOLEAN, true);
    public static final Schema BOOLEAN_MAP = map("BooleanMap", PreludeSchemas.BOOLEAN, false);
    public static final Schema SPARSE_BOOLEAN_MAP = map("SparseBooleanMap", PreludeSchemas.BOOLEAN, true);
    public static final Schema BYTE_LIST = list("ByteList", PreludeSchemas.BYTE, false);
    public static final Schema SPARSE_BYTE_LIST = list("SparseByteList", PreludeSchemas.BYTE, true);
    public static final Schema BYTE_MAP = map("ByteMap", PreludeSchemas.BYTE, false);
    public static final Schema SPARSE_BYTE_MAP = map("SparseByteMap", PreludeSchemas.BYTE, true);
    public static final Schema SHORT_LIST = list("ShortList", PreludeSchemas.SHORT, false);
    public static final Schema SPARSE_SHORT_LIST = list("SparseShortList", PreludeSchemas.SHORT, true);
    public static final Schema SHORT_MAP = map("ShortMap", PreludeSchemas.SHORT, false);
    public static final Schema SPARSE_SHORT_MAP = map("SparseShortMap", PreludeSchemas.SHORT, true);
    public static final Schema INTEGER_LIST = list("IntegerList", PreludeSchemas.INTEGER, false);
    public static final Schema SPARSE_INTEGER_LIST = list("SparseIntegerList", PreludeSchemas.INTEGER, true);
    public static final Schema INTEGER_MAP = map("IntegerMap", PreludeSchemas.INTEGER, false);
    public static final Schema SPARSE_INTEGER_MAP = map("SparseIntegerMap", PreludeSchemas.INTEGER, true);
    public static final Schema LONG_LIST = list("LongList", PreludeSchemas.LONG, false);
    public static final Schema SPARSE_LONG_LIST = list("SparseLongList", PreludeSchemas.LONG, true);
    public static final Schema LONG_MAP = map("LongMap", PreludeSchemas.LONG, false);
    public static final Schema SPARSE_LONG_MAP = map("SparseLongMap", PreludeSchemas.LONG, true);
    public static final Schema FLOAT_LIST = list("FloatList", PreludeSchemas.FLOAT, false);
    public static final Schema SPARSE_FLOAT_LIST = list("SparseFloatList", PreludeSchemas.FLOAT, true);
    public static final Schema FLOAT_MAP = map("FloatMap", PreludeSchemas.FLOAT, false);
    public static final Schema SPARSE_FLOAT_MAP = map("SparseFloatMap", PreludeSchemas.FLOAT, true);
    public static final Schema DOUBLE_LIST = list("DoubleList", PreludeSchemas.DOUBLE, false);
    public static final Schema SPARSE_DOUBLE_LIST = list("SparseDoubleList", PreludeSchemas.DOUBLE, true);
    public static final Schema DOUBLE_MAP = map("DoubleMap", PreludeSchemas.DOUBLE, false);
    public static final Schema SPARSE_DOUBLE_MAP = map("SparseDoubleMap", PreludeSchemas.DOUBLE, true);
    public static final Schema BIG_INTEGER_LIST = list("BigIntegerList", PreludeSchemas.BIG_INTEGER, false);
    public static final Schema SPARSE_BIG_INTEGER_LIST = list("SparseBigIntegerList", PreludeSchemas.BIG_INTEGER, true);
    public static final Schema BIG_INTEGER_MAP = map("BigIntegerMap", PreludeSchemas.BIG_INTEGER, false);
    public static final Schema SPARSE_BIG_INTEGER_MAP = map("SparseBigIntegerMap", PreludeSchemas.BIG_INTEGER, true);
    public static final Schema BIG_DECIMAL_LIST = list("BigDecimalList", PreludeSchemas.BIG_DECIMAL, false);
    public static final Schema SPARSE_BIG_DECIMAL_LIST = list("SparseBigDecimalList", PreludeSchemas.BIG_DECIMAL, true);
    public static final Schema BIG_DECIMAL_MAP = map("BigDecimalMap", PreludeSchemas.BIG_DECIMAL, false);
    public static final Schema SPARSE_BIG_DECIMAL_MAP = map("SparseBigDecimalMap", PreludeSchemas.BIG_DECIMAL, true);
    public static final Schema STRING_LIST = list("StringList", PreludeSchemas.STRING, false);
    public static final Schema SPARSE_STRING_LIST = list("SparseStringList", PreludeSchemas.STRING, true);
    public static final Schema STRING_MAP = map("StringMap", PreludeSchemas.STRING, false);
    public static final Schema SPARSE_STRING_MAP = map("SparseStringMap", PreludeSchemas.STRING, true);
    public static final Schema BLOB_LIST = list("BlobList", PreludeSchemas.BLOB, false);
    public static final Schema SPARSE_BLOB_LIST = list("SparseBlobList", PreludeSchemas.BLOB, true);
    public static final Schema BLOB_MAP = map("BlobMap", PreludeSchemas.BLOB, false);
    public static final Schema SPARSE_BLOB_MAP = map("SparseBlobMap", PreludeSchemas.BLOB, true);
    public static final Schema TIMESTAMP_LIST = list("TimestampList", PreludeSchemas.TIMESTAMP, false);
    public static final Schema SPARSE_TIMESTAMP_LIST = list("SparseTimestampList", PreludeSchemas.TIMESTAMP, true);
    public static final Schema TIMESTAMP_MAP = map("TimestampMap", PreludeSchemas.TIMESTAMP, false);
    public static final Schema SPARSE_TIMESTAMP_MAP = map("SparseTimestampMap", PreludeSchemas.TIMESTAMP, true);
    public static final Schema DOCUMENT_LIST = list("DocumentList", PreludeSchemas.DOCUMENT, false);
    public static final Schema SPARSE_DOCUMENT_LIST = list("SparseDocumentList", PreludeSchemas.DOCUMENT, true);
    public static final Schema DOCUMENT_MAP = map("DocumentMap", PreludeSchemas.DOCUMENT, false);
    public static final Schema SPARSE_DOCUMENT_MAP = map("SparseDocumentMap", PreludeSchemas.DOCUMENT, true);
    public static final Schema ENUM_LIST = list("EnumList", COLOR, false);
    public static final Schema SPARSE_ENUM_LIST = list("SparseEnumList", COLOR, true);
    public static final Schema ENUM_MAP = map("EnumMap", COLOR, false);
    public static final Schema SPARSE_ENUM_MAP = map("SparseEnumMap", COLOR, true);
    public static final Schema INT_ENUM_LIST = list("IntEnumList", LEVEL, false);
    public static final Schema SPARSE_INT_ENUM_LIST = list("SparseIntEnumList", LEVEL, true);
    public static final Schema INT_ENUM_MAP = map("IntEnumMap", LEVEL, false);
    public static final Schema SPARSE_INT_ENUM_MAP = map("SparseIntEnumMap", LEVEL, true);
    public static final Schema STRUCT_LIST = list("StructList", ITEM, false);
    public static final Schema SPARSE_STRUCT_LIST = list("SparseStructList", ITEM, true);
    public static final Schema STRUCT_MAP = map("StructMap", ITEM, false);
    public static final Schema SPARSE_STRUCT_MAP = map("SparseStructMap", ITEM, true);
    public static final Schema NESTED_LIST = list("NestedList", STRING_LIST, false);
    public static final Schema NESTED_MAP = map("NestedMap", STRING_LIST, false);

    public static final Schema SCHEMA = Schema.structureBuilder(id("Collections"))
            .putMember("booleanList", BOOLEAN_LIST)
            .putMember("sparseBooleanList", SPARSE_BOOLEAN_LIST)
            .putMember("booleanMap", BOOLEAN_MAP)
            .putMember("sparseBooleanMap", SPARSE_BOOLEAN_MAP)
            .putMember("byteList", BYTE_LIST)
            .putMember("sparseByteList", SPARSE_BYTE_LIST)
            .putMember("byteMap", BYTE_MAP)
            .putMember("sparseByteMap", SPARSE_BYTE_MAP)
            .putMember("shortList", SHORT_LIST)
            .putMember("sparseShortList", SPARSE_SHORT_LIST)
            .putMember("shortMap", SHORT_MAP)
            .putMember("sparseShortMap", SPARSE_SHORT_MAP)
            .putMember("integerList", INTEGER_LIST)
            .putMember("sparseIntegerList", SPARSE_INTEGER_LIST)
            .putMember("integerMap", INTEGER_MAP)
            .putMember("sparseIntegerMap", SPARSE_INTEGER_MAP)
            .putMember("longList", LONG_LIST)
            .putMember("sparseLongList", SPARSE_LONG_LIST)
            .putMember("longMap", LONG_MAP)
            .putMember("sparseLongMap", SPARSE_LONG_MAP)
            .putMember("floatList", FLOAT_LIST)
            .putMember("sparseFloatList", SPARSE_FLOAT_LIST)
            .putMember("floatMap", FLOAT_MAP)
            .putMember("sparseFloatMap", SPARSE_FLOAT_MAP)
            .putMember("doubleList", DOUBLE_LIST)
            .putMember("sparseDoubleList", SPARSE_DOUBLE_LIST)
            .putMember("doubleMap", DOUBLE_MAP)
            .putMember("sparseDoubleMap", SPARSE_DOUBLE_MAP)
            .putMember("bigIntegerList", BIG_INTEGER_LIST)
            .putMember("sparseBigIntegerList", SPARSE_BIG_INTEGER_LIST)
            .putMember("bigIntegerMap", BIG_INTEGER_MAP)
            .putMember("sparseBigIntegerMap", SPARSE_BIG_INTEGER_MAP)
            .putMember("bigDecimalList", BIG_DECIMAL_LIST)
            .putMember("sparseBigDecimalList", SPARSE_BIG_DECIMAL_LIST)
            .putMember("bigDecimalMap", BIG_DECIMAL_MAP)
            .putMember("sparseBigDecimalMap", SPARSE_BIG_DECIMAL_MAP)
            .putMember("stringList", STRING_LIST)
            .putMember("sparseStringList", SPARSE_STRING_LIST)
            .putMember("stringMap", STRING_MAP)
            .putMember("sparseStringMap", SPARSE_STRING_MAP)
            .putMember("blobList", BLOB_LIST)
            .putMember("sparseBlobList", SPARSE_BLOB_LIST)
            .putMember("blobMap", BLOB_MAP)
            .putMember("sparseBlobMap", SPARSE_BLOB_MAP)
            .putMember("timestampList", TIMESTAMP_LIST)
            .putMember("sparseTimestampList", SPARSE_TIMESTAMP_LIST)
            .putMember("timestampMap", TIMESTAMP_MAP)
            .putMember("sparseTimestampMap", SPARSE_TIMESTAMP_MAP)
            .putMember("documentList", DOCUMENT_LIST)
            .putMember("sparseDocumentList", SPARSE_DOCUMENT_LIST)
            .putMember("documentMap", DOCUMENT_MAP)
            .putMember("sparseDocumentMap", SPARSE_DOCUMENT_MAP)
            .putMember("enumList", ENUM_LIST)
            .putMember("sparseEnumList", SPARSE_ENUM_LIST)
            .putMember("enumMap", ENUM_MAP)
            .putMember("sparseEnumMap", SPARSE_ENUM_MAP)
            .putMember("intEnumList", INT_ENUM_LIST)
            .putMember("sparseIntEnumList", SPARSE_INT_ENUM_LIST)
            .putMember("intEnumMap", INT_ENUM_MAP)
            .putMember("sparseIntEnumMap", SPARSE_INT_ENUM_MAP)
            .putMember("structList", STRUCT_LIST)
            .putMember("sparseStructList", SPARSE_STRUCT_LIST)
            .putMember("structMap", STRUCT_MAP)
            .putMember("sparseStructMap", SPARSE_STRUCT_MAP)
            .putMember("nestedList", NESTED_LIST)
            .putMember("nestedMap", NESTED_MAP)
            .build();

    private static final Schema M_BOOLEAN_LIST = SCHEMA.member("booleanList");
    private static final Schema M_SPARSE_BOOLEAN_LIST = SCHEMA.member("sparseBooleanList");
    private static final Schema M_BOOLEAN_MAP = SCHEMA.member("booleanMap");
    private static final Schema M_SPARSE_BOOLEAN_MAP = SCHEMA.member("sparseBooleanMap");
    private static final Schema M_BYTE_LIST = SCHEMA.member("byteList");
    private static final Schema M_SPARSE_BYTE_LIST = SCHEMA.member("sparseByteList");
    private static final Schema M_BYTE_MAP = SCHEMA.member("byteMap");
    private static final Schema M_SPARSE_BYTE_MAP = SCHEMA.member("sparseByteMap");
    private static final Schema M_SHORT_LIST = SCHEMA.member("shortList");
    private static final Schema M_SPARSE_SHORT_LIST = SCHEMA.member("sparseShortList");
    private static final Schema M_SHORT_MAP = SCHEMA.member("shortMap");
    private static final Schema M_SPARSE_SHORT_MAP = SCHEMA.member("sparseShortMap");
    private static final Schema M_INTEGER_LIST = SCHEMA.member("integerList");
    private static final Schema M_SPARSE_INTEGER_LIST = SCHEMA.member("sparseIntegerList");
    private static final Schema M_INTEGER_MAP = SCHEMA.member("integerMap");
    private static final Schema M_SPARSE_INTEGER_MAP = SCHEMA.member("sparseIntegerMap");
    private static final Schema M_LONG_LIST = SCHEMA.member("longList");
    private static final Schema M_SPARSE_LONG_LIST = SCHEMA.member("sparseLongList");
    private static final Schema M_LONG_MAP = SCHEMA.member("longMap");
    private static final Schema M_SPARSE_LONG_MAP = SCHEMA.member("sparseLongMap");
    private static final Schema M_FLOAT_LIST = SCHEMA.member("floatList");
    private static final Schema M_SPARSE_FLOAT_LIST = SCHEMA.member("sparseFloatList");
    private static final Schema M_FLOAT_MAP = SCHEMA.member("floatMap");
    private static final Schema M_SPARSE_FLOAT_MAP = SCHEMA.member("sparseFloatMap");
    private static final Schema M_DOUBLE_LIST = SCHEMA.member("doubleList");
    private static final Schema M_SPARSE_DOUBLE_LIST = SCHEMA.member("sparseDoubleList");
    private static final Schema M_DOUBLE_MAP = SCHEMA.member("doubleMap");
    private static final Schema M_SPARSE_DOUBLE_MAP = SCHEMA.member("sparseDoubleMap");
    private static final Schema M_BIG_INTEGER_LIST = SCHEMA.member("bigIntegerList");
    private static final Schema M_SPARSE_BIG_INTEGER_LIST = SCHEMA.member("sparseBigIntegerList");
    private static final Schema M_BIG_INTEGER_MAP = SCHEMA.member("bigIntegerMap");
    private static final Schema M_SPARSE_BIG_INTEGER_MAP = SCHEMA.member("sparseBigIntegerMap");
    private static final Schema M_BIG_DECIMAL_LIST = SCHEMA.member("bigDecimalList");
    private static final Schema M_SPARSE_BIG_DECIMAL_LIST = SCHEMA.member("sparseBigDecimalList");
    private static final Schema M_BIG_DECIMAL_MAP = SCHEMA.member("bigDecimalMap");
    private static final Schema M_SPARSE_BIG_DECIMAL_MAP = SCHEMA.member("sparseBigDecimalMap");
    private static final Schema M_STRING_LIST = SCHEMA.member("stringList");
    private static final Schema M_SPARSE_STRING_LIST = SCHEMA.member("sparseStringList");
    private static final Schema M_STRING_MAP = SCHEMA.member("stringMap");
    private static final Schema M_SPARSE_STRING_MAP = SCHEMA.member("sparseStringMap");
    private static final Schema M_BLOB_LIST = SCHEMA.member("blobList");
    private static final Schema M_SPARSE_BLOB_LIST = SCHEMA.member("sparseBlobList");
    private static final Schema M_BLOB_MAP = SCHEMA.member("blobMap");
    private static final Schema M_SPARSE_BLOB_MAP = SCHEMA.member("sparseBlobMap");
    private static final Schema M_TIMESTAMP_LIST = SCHEMA.member("timestampList");
    private static final Schema M_SPARSE_TIMESTAMP_LIST = SCHEMA.member("sparseTimestampList");
    private static final Schema M_TIMESTAMP_MAP = SCHEMA.member("timestampMap");
    private static final Schema M_SPARSE_TIMESTAMP_MAP = SCHEMA.member("sparseTimestampMap");
    private static final Schema M_DOCUMENT_LIST = SCHEMA.member("documentList");
    private static final Schema M_SPARSE_DOCUMENT_LIST = SCHEMA.member("sparseDocumentList");
    private static final Schema M_DOCUMENT_MAP = SCHEMA.member("documentMap");
    private static final Schema M_SPARSE_DOCUMENT_MAP = SCHEMA.member("sparseDocumentMap");
    private static final Schema M_ENUM_LIST = SCHEMA.member("enumList");
    private static final Schema M_SPARSE_ENUM_LIST = SCHEMA.member("sparseEnumList");
    private static final Schema M_ENUM_MAP = SCHEMA.member("enumMap");
    private static final Schema M_SPARSE_ENUM_MAP = SCHEMA.member("sparseEnumMap");
    private static final Schema M_INT_ENUM_LIST = SCHEMA.member("intEnumList");
    private static final Schema M_SPARSE_INT_ENUM_LIST = SCHEMA.member("sparseIntEnumList");
    private static final Schema M_INT_ENUM_MAP = SCHEMA.member("intEnumMap");
    private static final Schema M_SPARSE_INT_ENUM_MAP = SCHEMA.member("sparseIntEnumMap");
    private static final Schema M_STRUCT_LIST = SCHEMA.member("structList");
    private static final Schema M_SPARSE_STRUCT_LIST = SCHEMA.member("sparseStructList");
    private static final Schema M_STRUCT_MAP = SCHEMA.member("structMap");
    private static final Schema M_SPARSE_STRUCT_MAP = SCHEMA.member("sparseStructMap");
    private static final Schema M_NESTED_LIST = SCHEMA.member("nestedList");
    private static final Schema M_NESTED_MAP = SCHEMA.member("nestedMap");

    public static SerializableStruct typed(Options options) {
        return new Typed(options);
    }

    public static SerializableStruct generic(Options options) {
        return new Generic(options);
    }

    public static final class Color implements SmithyEnum {
        public static final Color RED = new Color("RED");
        public static final Color BLUE = new Color("BLUE");

        private final String value;

        private Color(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    public static final class Level implements SmithyIntEnum {
        public static final Level LOW = new Level(1);
        public static final Level HIGH = new Level(2);

        private final int value;

        private Level(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }
    }

    public static final class Item implements SerializableStruct {
        private static final Schema VALUE = ITEM.member("value");
        private final String value;

        public Item(String value) {
            this.value = value;
        }

        @Override
        public Schema schema() {
            return ITEM;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(VALUE, value);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private static <V> Map<String, V> mapOf(V first, V second) {
        var map = new LinkedHashMap<String, V>();
        map.put("k1", first);
        map.put("k2", second);
        return map;
    }

    /**
     * Use random-access dense lists and linked sparse lists to test both codec iteration paths.
     */
    private abstract static class Values implements SerializableStruct {
        final Options options;

        final List<Boolean> booleanList = List.of(true, false);
        final List<Boolean> sparseBooleanList = new LinkedList<>(Arrays.asList(true, null));
        final Map<String, Boolean> booleanMap = mapOf(List.of(true, false).get(0), List.of(true, false).get(1));
        final Map<String, Boolean> sparseBooleanMap = mapOf(true, null);
        final List<Byte> byteList = List.of((byte) 1, (byte) -2);
        final List<Byte> sparseByteList = new LinkedList<>(Arrays.asList((byte) 1, null));
        final Map<String, Byte> byteMap =
                mapOf(List.of((byte) 1, (byte) -2).get(0), List.of((byte) 1, (byte) -2).get(1));
        final Map<String, Byte> sparseByteMap = mapOf((byte) 1, null);
        final List<Short> shortList = List.of((short) 3, (short) -4);
        final List<Short> sparseShortList = new LinkedList<>(Arrays.asList((short) 3, null));
        final Map<String, Short> shortMap =
                mapOf(List.of((short) 3, (short) -4).get(0), List.of((short) 3, (short) -4).get(1));
        final Map<String, Short> sparseShortMap = mapOf((short) 3, null);
        final List<Integer> integerList = List.of(5, -6);
        final List<Integer> sparseIntegerList = new LinkedList<>(Arrays.asList(5, null));
        final Map<String, Integer> integerMap = mapOf(List.of(5, -6).get(0), List.of(5, -6).get(1));
        final Map<String, Integer> sparseIntegerMap = mapOf(5, null);
        final List<Long> longList = List.of(7L, -8L);
        final List<Long> sparseLongList = new LinkedList<>(Arrays.asList(7L, null));
        final Map<String, Long> longMap = mapOf(List.of(7L, -8L).get(0), List.of(7L, -8L).get(1));
        final Map<String, Long> sparseLongMap = mapOf(7L, null);
        final List<Float> floatList = List.of(1.5f, -2.25f);
        final List<Float> sparseFloatList = new LinkedList<>(Arrays.asList(1.5f, null));
        final Map<String, Float> floatMap = mapOf(List.of(1.5f, -2.25f).get(0), List.of(1.5f, -2.25f).get(1));
        final Map<String, Float> sparseFloatMap = mapOf(1.5f, null);
        final List<Double> doubleList = List.of(3.5, -4.75);
        final List<Double> sparseDoubleList = new LinkedList<>(Arrays.asList(3.5, null));
        final Map<String, Double> doubleMap = mapOf(List.of(3.5, -4.75).get(0), List.of(3.5, -4.75).get(1));
        final Map<String, Double> sparseDoubleMap = mapOf(3.5, null);
        final List<BigInteger> bigIntegerList = List.of(BigInteger.TEN, BigInteger.valueOf(-11));
        final List<BigInteger> sparseBigIntegerList = new LinkedList<>(Arrays.asList(BigInteger.TEN, null));
        final Map<String, BigInteger> bigIntegerMap = mapOf(List.of(BigInteger.TEN, BigInteger.valueOf(-11)).get(0),
                List.of(BigInteger.TEN, BigInteger.valueOf(-11)).get(1));
        final Map<String, BigInteger> sparseBigIntegerMap = mapOf(BigInteger.TEN, null);
        final List<BigDecimal> bigDecimalList = List.of(new BigDecimal("12.5"), new BigDecimal("-13.25"));
        final List<BigDecimal> sparseBigDecimalList = new LinkedList<>(Arrays.asList(new BigDecimal("12.5"), null));
        final Map<String, BigDecimal> bigDecimalMap =
                mapOf(List.of(new BigDecimal("12.5"), new BigDecimal("-13.25")).get(0),
                        List.of(new BigDecimal("12.5"), new BigDecimal("-13.25")).get(1));
        final Map<String, BigDecimal> sparseBigDecimalMap = mapOf(new BigDecimal("12.5"), null);
        final List<String> stringList = List.of("a", "b c");
        final List<String> sparseStringList = new LinkedList<>(Arrays.asList("a", null));
        final Map<String, String> stringMap = mapOf(List.of("a", "b c").get(0), List.of("a", "b c").get(1));
        final Map<String, String> sparseStringMap = mapOf("a", null);
        final List<ByteBuffer> blobList =
                List.of(ByteBuffer.wrap(new byte[] {1, 2, 3}), ByteBuffer.wrap(new byte[] {4}));
        final List<ByteBuffer> sparseBlobList =
                new LinkedList<>(Arrays.asList(ByteBuffer.wrap(new byte[] {1, 2, 3}), null));
        final Map<String, ByteBuffer> blobMap =
                mapOf(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3}), ByteBuffer.wrap(new byte[] {4})).get(0),
                        List.of(ByteBuffer.wrap(new byte[] {1, 2, 3}), ByteBuffer.wrap(new byte[] {4})).get(1));
        final Map<String, ByteBuffer> sparseBlobMap = mapOf(ByteBuffer.wrap(new byte[] {1, 2, 3}), null);
        final List<Instant> timestampList = List.of(Instant.ofEpochSecond(1700000000L), Instant.EPOCH);
        final List<Instant> sparseTimestampList =
                new LinkedList<>(Arrays.asList(Instant.ofEpochSecond(1700000000L), null));
        final Map<String, Instant> timestampMap =
                mapOf(List.of(Instant.ofEpochSecond(1700000000L), Instant.EPOCH).get(0),
                        List.of(Instant.ofEpochSecond(1700000000L), Instant.EPOCH).get(1));
        final Map<String, Instant> sparseTimestampMap = mapOf(Instant.ofEpochSecond(1700000000L), null);
        final List<Document> documentList = List.of(Document.of("doc"), Document.of(42));
        final List<Document> sparseDocumentList = new LinkedList<>(Arrays.asList(Document.of("doc"), null));
        final Map<String, Document> documentMap = mapOf(List.of(Document.of("doc"), Document.of(42)).get(0),
                List.of(Document.of("doc"), Document.of(42)).get(1));
        final Map<String, Document> sparseDocumentMap = mapOf(Document.of("doc"), null);
        final List<Color> enumList = List.of(Color.RED, Color.BLUE);
        final List<Color> sparseEnumList = new LinkedList<>(Arrays.asList(Color.RED, null));
        final Map<String, Color> enumMap =
                mapOf(List.of(Color.RED, Color.BLUE).get(0), List.of(Color.RED, Color.BLUE).get(1));
        final Map<String, Color> sparseEnumMap = mapOf(Color.RED, null);
        final List<Level> intEnumList = List.of(Level.LOW, Level.HIGH);
        final List<Level> sparseIntEnumList = new LinkedList<>(Arrays.asList(Level.LOW, null));
        final Map<String, Level> intEnumMap =
                mapOf(List.of(Level.LOW, Level.HIGH).get(0), List.of(Level.LOW, Level.HIGH).get(1));
        final Map<String, Level> sparseIntEnumMap = mapOf(Level.LOW, null);
        final List<Item> structList = List.of(new Item("x"), new Item("y"));
        final List<Item> sparseStructList = new LinkedList<>(Arrays.asList(new Item("x"), null));
        final Map<String, Item> structMap =
                mapOf(List.of(new Item("x"), new Item("y")).get(0), List.of(new Item("x"), new Item("y")).get(1));
        final Map<String, Item> sparseStructMap = mapOf(new Item("x"), null);
        final List<List<String>> nestedList = List.of(List.of("a", "b"), List.of());
        final Map<String, List<String>> nestedMap = mapOf(List.of("c"), List.of("d", "e"));

        Values(Options options) {
            this.options = options;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Typed extends Values {
        Typed(Options options) {
            super(options);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBooleanList(M_BOOLEAN_LIST, booleanList, M_BOOLEAN_LIST.listMember());
            serializer.writeSparseBooleanList(M_SPARSE_BOOLEAN_LIST,
                    sparseBooleanList,
                    M_SPARSE_BOOLEAN_LIST.listMember());
            serializer.writeByteList(M_BYTE_LIST, byteList, M_BYTE_LIST.listMember());
            serializer.writeSparseByteList(M_SPARSE_BYTE_LIST, sparseByteList, M_SPARSE_BYTE_LIST.listMember());
            serializer.writeShortList(M_SHORT_LIST, shortList, M_SHORT_LIST.listMember());
            serializer.writeSparseShortList(M_SPARSE_SHORT_LIST, sparseShortList, M_SPARSE_SHORT_LIST.listMember());
            serializer.writeIntegerList(M_INTEGER_LIST, integerList, M_INTEGER_LIST.listMember());
            serializer.writeSparseIntegerList(M_SPARSE_INTEGER_LIST,
                    sparseIntegerList,
                    M_SPARSE_INTEGER_LIST.listMember());
            serializer.writeLongList(M_LONG_LIST, longList, M_LONG_LIST.listMember());
            serializer.writeSparseLongList(M_SPARSE_LONG_LIST, sparseLongList, M_SPARSE_LONG_LIST.listMember());
            serializer.writeFloatList(M_FLOAT_LIST, floatList, M_FLOAT_LIST.listMember());
            serializer.writeSparseFloatList(M_SPARSE_FLOAT_LIST, sparseFloatList, M_SPARSE_FLOAT_LIST.listMember());
            serializer.writeDoubleList(M_DOUBLE_LIST, doubleList, M_DOUBLE_LIST.listMember());
            serializer.writeSparseDoubleList(M_SPARSE_DOUBLE_LIST, sparseDoubleList, M_SPARSE_DOUBLE_LIST.listMember());
            serializer.writeBigIntegerList(M_BIG_INTEGER_LIST, bigIntegerList, M_BIG_INTEGER_LIST.listMember());
            serializer.writeSparseBigIntegerList(M_SPARSE_BIG_INTEGER_LIST,
                    sparseBigIntegerList,
                    M_SPARSE_BIG_INTEGER_LIST.listMember());
            serializer.writeBigDecimalList(M_BIG_DECIMAL_LIST, bigDecimalList, M_BIG_DECIMAL_LIST.listMember());
            serializer.writeSparseBigDecimalList(M_SPARSE_BIG_DECIMAL_LIST,
                    sparseBigDecimalList,
                    M_SPARSE_BIG_DECIMAL_LIST.listMember());
            serializer.writeStringList(M_STRING_LIST, stringList, M_STRING_LIST.listMember());
            serializer.writeSparseStringList(M_SPARSE_STRING_LIST, sparseStringList, M_SPARSE_STRING_LIST.listMember());
            serializer.writeBlobList(M_BLOB_LIST, blobList, M_BLOB_LIST.listMember());
            serializer.writeSparseBlobList(M_SPARSE_BLOB_LIST, sparseBlobList, M_SPARSE_BLOB_LIST.listMember());
            serializer.writeTimestampList(M_TIMESTAMP_LIST, timestampList, M_TIMESTAMP_LIST.listMember());
            serializer.writeSparseTimestampList(M_SPARSE_TIMESTAMP_LIST,
                    sparseTimestampList,
                    M_SPARSE_TIMESTAMP_LIST.listMember());
            if (options.documents()) {
                serializer.writeDocumentList(M_DOCUMENT_LIST, documentList, M_DOCUMENT_LIST.listMember());
                serializer.writeSparseDocumentList(M_SPARSE_DOCUMENT_LIST,
                        sparseDocumentList,
                        M_SPARSE_DOCUMENT_LIST.listMember());
            }
            serializer.writeEnumList(M_ENUM_LIST, enumList, M_ENUM_LIST.listMember());
            serializer.writeSparseEnumList(M_SPARSE_ENUM_LIST, sparseEnumList, M_SPARSE_ENUM_LIST.listMember());
            serializer.writeIntEnumList(M_INT_ENUM_LIST, intEnumList, M_INT_ENUM_LIST.listMember());
            serializer.writeSparseIntEnumList(M_SPARSE_INT_ENUM_LIST,
                    sparseIntEnumList,
                    M_SPARSE_INT_ENUM_LIST.listMember());
            serializer.writeStructList(M_STRUCT_LIST, structList, M_STRUCT_LIST.listMember());
            serializer.writeSparseStructList(M_SPARSE_STRUCT_LIST, sparseStructList, M_SPARSE_STRUCT_LIST.listMember());
            serializer.writeList(M_NESTED_LIST, nestedList, nestedList.size(), Typed::writeNestedList);
            if (!options.maps()) {
                return;
            }
            serializer.writeBooleanMap(M_BOOLEAN_MAP,
                    booleanMap,
                    M_BOOLEAN_MAP.mapKeyMember(),
                    M_BOOLEAN_MAP.mapValueMember());
            serializer.writeSparseBooleanMap(
                    M_SPARSE_BOOLEAN_MAP,
                    sparseBooleanMap,
                    M_SPARSE_BOOLEAN_MAP.mapKeyMember(),
                    M_SPARSE_BOOLEAN_MAP.mapValueMember());
            serializer.writeByteMap(M_BYTE_MAP, byteMap, M_BYTE_MAP.mapKeyMember(), M_BYTE_MAP.mapValueMember());
            serializer.writeSparseByteMap(
                    M_SPARSE_BYTE_MAP,
                    sparseByteMap,
                    M_SPARSE_BYTE_MAP.mapKeyMember(),
                    M_SPARSE_BYTE_MAP.mapValueMember());
            serializer.writeShortMap(M_SHORT_MAP, shortMap, M_SHORT_MAP.mapKeyMember(), M_SHORT_MAP.mapValueMember());
            serializer.writeSparseShortMap(
                    M_SPARSE_SHORT_MAP,
                    sparseShortMap,
                    M_SPARSE_SHORT_MAP.mapKeyMember(),
                    M_SPARSE_SHORT_MAP.mapValueMember());
            serializer.writeIntegerMap(M_INTEGER_MAP,
                    integerMap,
                    M_INTEGER_MAP.mapKeyMember(),
                    M_INTEGER_MAP.mapValueMember());
            serializer.writeSparseIntegerMap(
                    M_SPARSE_INTEGER_MAP,
                    sparseIntegerMap,
                    M_SPARSE_INTEGER_MAP.mapKeyMember(),
                    M_SPARSE_INTEGER_MAP.mapValueMember());
            serializer.writeLongMap(M_LONG_MAP, longMap, M_LONG_MAP.mapKeyMember(), M_LONG_MAP.mapValueMember());
            serializer.writeSparseLongMap(
                    M_SPARSE_LONG_MAP,
                    sparseLongMap,
                    M_SPARSE_LONG_MAP.mapKeyMember(),
                    M_SPARSE_LONG_MAP.mapValueMember());
            serializer.writeFloatMap(M_FLOAT_MAP, floatMap, M_FLOAT_MAP.mapKeyMember(), M_FLOAT_MAP.mapValueMember());
            serializer.writeSparseFloatMap(
                    M_SPARSE_FLOAT_MAP,
                    sparseFloatMap,
                    M_SPARSE_FLOAT_MAP.mapKeyMember(),
                    M_SPARSE_FLOAT_MAP.mapValueMember());
            serializer.writeDoubleMap(M_DOUBLE_MAP,
                    doubleMap,
                    M_DOUBLE_MAP.mapKeyMember(),
                    M_DOUBLE_MAP.mapValueMember());
            serializer.writeSparseDoubleMap(
                    M_SPARSE_DOUBLE_MAP,
                    sparseDoubleMap,
                    M_SPARSE_DOUBLE_MAP.mapKeyMember(),
                    M_SPARSE_DOUBLE_MAP.mapValueMember());
            serializer.writeBigIntegerMap(M_BIG_INTEGER_MAP,
                    bigIntegerMap,
                    M_BIG_INTEGER_MAP.mapKeyMember(),
                    M_BIG_INTEGER_MAP.mapValueMember());
            serializer.writeSparseBigIntegerMap(
                    M_SPARSE_BIG_INTEGER_MAP,
                    sparseBigIntegerMap,
                    M_SPARSE_BIG_INTEGER_MAP.mapKeyMember(),
                    M_SPARSE_BIG_INTEGER_MAP.mapValueMember());
            serializer.writeBigDecimalMap(M_BIG_DECIMAL_MAP,
                    bigDecimalMap,
                    M_BIG_DECIMAL_MAP.mapKeyMember(),
                    M_BIG_DECIMAL_MAP.mapValueMember());
            serializer.writeSparseBigDecimalMap(
                    M_SPARSE_BIG_DECIMAL_MAP,
                    sparseBigDecimalMap,
                    M_SPARSE_BIG_DECIMAL_MAP.mapKeyMember(),
                    M_SPARSE_BIG_DECIMAL_MAP.mapValueMember());
            serializer.writeStringMap(M_STRING_MAP,
                    stringMap,
                    M_STRING_MAP.mapKeyMember(),
                    M_STRING_MAP.mapValueMember());
            serializer.writeSparseStringMap(
                    M_SPARSE_STRING_MAP,
                    sparseStringMap,
                    M_SPARSE_STRING_MAP.mapKeyMember(),
                    M_SPARSE_STRING_MAP.mapValueMember());
            serializer.writeBlobMap(M_BLOB_MAP, blobMap, M_BLOB_MAP.mapKeyMember(), M_BLOB_MAP.mapValueMember());
            serializer.writeSparseBlobMap(
                    M_SPARSE_BLOB_MAP,
                    sparseBlobMap,
                    M_SPARSE_BLOB_MAP.mapKeyMember(),
                    M_SPARSE_BLOB_MAP.mapValueMember());
            serializer.writeTimestampMap(M_TIMESTAMP_MAP,
                    timestampMap,
                    M_TIMESTAMP_MAP.mapKeyMember(),
                    M_TIMESTAMP_MAP.mapValueMember());
            serializer.writeSparseTimestampMap(
                    M_SPARSE_TIMESTAMP_MAP,
                    sparseTimestampMap,
                    M_SPARSE_TIMESTAMP_MAP.mapKeyMember(),
                    M_SPARSE_TIMESTAMP_MAP.mapValueMember());
            if (options.documents()) {
                serializer.writeDocumentMap(M_DOCUMENT_MAP,
                        documentMap,
                        M_DOCUMENT_MAP.mapKeyMember(),
                        M_DOCUMENT_MAP.mapValueMember());
                serializer.writeSparseDocumentMap(
                        M_SPARSE_DOCUMENT_MAP,
                        sparseDocumentMap,
                        M_SPARSE_DOCUMENT_MAP.mapKeyMember(),
                        M_SPARSE_DOCUMENT_MAP.mapValueMember());
            }
            serializer.writeEnumMap(M_ENUM_MAP, enumMap, M_ENUM_MAP.mapKeyMember(), M_ENUM_MAP.mapValueMember());
            serializer.writeSparseEnumMap(
                    M_SPARSE_ENUM_MAP,
                    sparseEnumMap,
                    M_SPARSE_ENUM_MAP.mapKeyMember(),
                    M_SPARSE_ENUM_MAP.mapValueMember());
            serializer.writeIntEnumMap(M_INT_ENUM_MAP,
                    intEnumMap,
                    M_INT_ENUM_MAP.mapKeyMember(),
                    M_INT_ENUM_MAP.mapValueMember());
            serializer.writeSparseIntEnumMap(
                    M_SPARSE_INT_ENUM_MAP,
                    sparseIntEnumMap,
                    M_SPARSE_INT_ENUM_MAP.mapKeyMember(),
                    M_SPARSE_INT_ENUM_MAP.mapValueMember());
            serializer.writeStructMap(M_STRUCT_MAP,
                    structMap,
                    M_STRUCT_MAP.mapKeyMember(),
                    M_STRUCT_MAP.mapValueMember());
            serializer.writeSparseStructMap(
                    M_SPARSE_STRUCT_MAP,
                    sparseStructMap,
                    M_SPARSE_STRUCT_MAP.mapKeyMember(),
                    M_SPARSE_STRUCT_MAP.mapValueMember());
            serializer.writeMap(M_NESTED_MAP, nestedMap, nestedMap.size(), Typed::writeNestedMap);
        }

        private static void writeNestedList(List<List<String>> values, ShapeSerializer serializer) {
            var m = M_NESTED_LIST.listMember();
            for (var value : values) {
                serializer.writeStringList(m, value, m.listMember());
            }
        }

        private static void writeNestedMap(Map<String, List<String>> values, MapSerializer serializer) {
            var k = M_NESTED_MAP.mapKeyMember();
            var v = M_NESTED_MAP.mapValueMember();
            values.forEach((key, value) -> serializer.writeEntry(k, key, value, (vv, s) -> {
                s.writeStringList(v, vv, v.listMember());
            }));
        }
    }

    private static final class Generic extends Values {
        Generic(Options options) {
            super(options);
        }

        private interface Writer<T> {
            void write(ShapeSerializer serializer, Schema schema, T value);
        }

        private static <T> void list(ShapeSerializer serializer, Schema member, List<T> values, Writer<T> writer) {
            var element = member.listMember();
            serializer.writeList(member, values, values.size(), (list, s) -> {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(element);
                    } else {
                        writer.write(s, element, v);
                    }
                }
            });
        }

        private static <T> void map(
                ShapeSerializer serializer,
                Schema member,
                Map<String, T> values,
                Writer<T> writer
        ) {
            var key = member.mapKeyMember();
            var value = member.mapValueMember();
            serializer.writeMap(member, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
                ms.writeEntry(key, k, v, (vv, s) -> {
                    if (vv == null) {
                        s.writeNull(value);
                    } else {
                        writer.write(s, value, vv);
                    }
                });
            }));
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            list(serializer, M_BOOLEAN_LIST, booleanList, ShapeSerializer::writeBoolean);
            list(serializer, M_SPARSE_BOOLEAN_LIST, sparseBooleanList, ShapeSerializer::writeBoolean);
            list(serializer, M_BYTE_LIST, byteList, ShapeSerializer::writeByte);
            list(serializer, M_SPARSE_BYTE_LIST, sparseByteList, ShapeSerializer::writeByte);
            list(serializer, M_SHORT_LIST, shortList, ShapeSerializer::writeShort);
            list(serializer, M_SPARSE_SHORT_LIST, sparseShortList, ShapeSerializer::writeShort);
            list(serializer, M_INTEGER_LIST, integerList, ShapeSerializer::writeInteger);
            list(serializer, M_SPARSE_INTEGER_LIST, sparseIntegerList, ShapeSerializer::writeInteger);
            list(serializer, M_LONG_LIST, longList, ShapeSerializer::writeLong);
            list(serializer, M_SPARSE_LONG_LIST, sparseLongList, ShapeSerializer::writeLong);
            list(serializer, M_FLOAT_LIST, floatList, ShapeSerializer::writeFloat);
            list(serializer, M_SPARSE_FLOAT_LIST, sparseFloatList, ShapeSerializer::writeFloat);
            list(serializer, M_DOUBLE_LIST, doubleList, ShapeSerializer::writeDouble);
            list(serializer, M_SPARSE_DOUBLE_LIST, sparseDoubleList, ShapeSerializer::writeDouble);
            list(serializer, M_BIG_INTEGER_LIST, bigIntegerList, ShapeSerializer::writeBigInteger);
            list(serializer, M_SPARSE_BIG_INTEGER_LIST, sparseBigIntegerList, ShapeSerializer::writeBigInteger);
            list(serializer, M_BIG_DECIMAL_LIST, bigDecimalList, ShapeSerializer::writeBigDecimal);
            list(serializer, M_SPARSE_BIG_DECIMAL_LIST, sparseBigDecimalList, ShapeSerializer::writeBigDecimal);
            list(serializer, M_STRING_LIST, stringList, ShapeSerializer::writeString);
            list(serializer, M_SPARSE_STRING_LIST, sparseStringList, ShapeSerializer::writeString);
            list(serializer, M_BLOB_LIST, blobList, ShapeSerializer::writeBlob);
            list(serializer, M_SPARSE_BLOB_LIST, sparseBlobList, ShapeSerializer::writeBlob);
            list(serializer, M_TIMESTAMP_LIST, timestampList, ShapeSerializer::writeTimestamp);
            list(serializer, M_SPARSE_TIMESTAMP_LIST, sparseTimestampList, ShapeSerializer::writeTimestamp);
            if (options.documents()) {
                list(serializer, M_DOCUMENT_LIST, documentList, ShapeSerializer::writeDocument);
                list(serializer, M_SPARSE_DOCUMENT_LIST, sparseDocumentList, ShapeSerializer::writeDocument);
            }
            list(serializer, M_ENUM_LIST, enumList, (s, schema, v) -> s.writeString(schema, v.getValue()));
            list(serializer, M_SPARSE_ENUM_LIST, sparseEnumList, (s, schema, v) -> s.writeString(schema, v.getValue()));
            list(serializer, M_INT_ENUM_LIST, intEnumList, (s, schema, v) -> s.writeInteger(schema, v.getValue()));
            list(serializer,
                    M_SPARSE_INT_ENUM_LIST,
                    sparseIntEnumList,
                    (s, schema, v) -> s.writeInteger(schema, v.getValue()));
            list(serializer, M_STRUCT_LIST, structList, ShapeSerializer::writeStruct);
            list(serializer, M_SPARSE_STRUCT_LIST, sparseStructList, ShapeSerializer::writeStruct);
            list(serializer,
                    M_NESTED_LIST,
                    nestedList,
                    (s, schema, v) -> list(s, schema, v, ShapeSerializer::writeString));
            if (!options.maps()) {
                return;
            }
            map(serializer, M_BOOLEAN_MAP, booleanMap, ShapeSerializer::writeBoolean);
            map(serializer, M_SPARSE_BOOLEAN_MAP, sparseBooleanMap, ShapeSerializer::writeBoolean);
            map(serializer, M_BYTE_MAP, byteMap, ShapeSerializer::writeByte);
            map(serializer, M_SPARSE_BYTE_MAP, sparseByteMap, ShapeSerializer::writeByte);
            map(serializer, M_SHORT_MAP, shortMap, ShapeSerializer::writeShort);
            map(serializer, M_SPARSE_SHORT_MAP, sparseShortMap, ShapeSerializer::writeShort);
            map(serializer, M_INTEGER_MAP, integerMap, ShapeSerializer::writeInteger);
            map(serializer, M_SPARSE_INTEGER_MAP, sparseIntegerMap, ShapeSerializer::writeInteger);
            map(serializer, M_LONG_MAP, longMap, ShapeSerializer::writeLong);
            map(serializer, M_SPARSE_LONG_MAP, sparseLongMap, ShapeSerializer::writeLong);
            map(serializer, M_FLOAT_MAP, floatMap, ShapeSerializer::writeFloat);
            map(serializer, M_SPARSE_FLOAT_MAP, sparseFloatMap, ShapeSerializer::writeFloat);
            map(serializer, M_DOUBLE_MAP, doubleMap, ShapeSerializer::writeDouble);
            map(serializer, M_SPARSE_DOUBLE_MAP, sparseDoubleMap, ShapeSerializer::writeDouble);
            map(serializer, M_BIG_INTEGER_MAP, bigIntegerMap, ShapeSerializer::writeBigInteger);
            map(serializer, M_SPARSE_BIG_INTEGER_MAP, sparseBigIntegerMap, ShapeSerializer::writeBigInteger);
            map(serializer, M_BIG_DECIMAL_MAP, bigDecimalMap, ShapeSerializer::writeBigDecimal);
            map(serializer, M_SPARSE_BIG_DECIMAL_MAP, sparseBigDecimalMap, ShapeSerializer::writeBigDecimal);
            map(serializer, M_STRING_MAP, stringMap, ShapeSerializer::writeString);
            map(serializer, M_SPARSE_STRING_MAP, sparseStringMap, ShapeSerializer::writeString);
            map(serializer, M_BLOB_MAP, blobMap, ShapeSerializer::writeBlob);
            map(serializer, M_SPARSE_BLOB_MAP, sparseBlobMap, ShapeSerializer::writeBlob);
            map(serializer, M_TIMESTAMP_MAP, timestampMap, ShapeSerializer::writeTimestamp);
            map(serializer, M_SPARSE_TIMESTAMP_MAP, sparseTimestampMap, ShapeSerializer::writeTimestamp);
            if (options.documents()) {
                map(serializer, M_DOCUMENT_MAP, documentMap, ShapeSerializer::writeDocument);
                map(serializer, M_SPARSE_DOCUMENT_MAP, sparseDocumentMap, ShapeSerializer::writeDocument);
            }
            map(serializer, M_ENUM_MAP, enumMap, (s, schema, v) -> s.writeString(schema, v.getValue()));
            map(serializer, M_SPARSE_ENUM_MAP, sparseEnumMap, (s, schema, v) -> s.writeString(schema, v.getValue()));
            map(serializer, M_INT_ENUM_MAP, intEnumMap, (s, schema, v) -> s.writeInteger(schema, v.getValue()));
            map(serializer,
                    M_SPARSE_INT_ENUM_MAP,
                    sparseIntEnumMap,
                    (s, schema, v) -> s.writeInteger(schema, v.getValue()));
            map(serializer, M_STRUCT_MAP, structMap, ShapeSerializer::writeStruct);
            map(serializer, M_SPARSE_STRUCT_MAP, sparseStructMap, ShapeSerializer::writeStruct);
            map(serializer,
                    M_NESTED_MAP,
                    nestedMap,
                    (s, schema, v) -> list(s, schema, v, ShapeSerializer::writeString));
        }
    }
}
