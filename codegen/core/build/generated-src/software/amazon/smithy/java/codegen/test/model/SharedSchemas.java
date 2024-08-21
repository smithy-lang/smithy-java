

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SchemaBuilder;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 * Defines shared shapes across the model package that are not part of another code-generated type.
 */
final class SharedSchemas {
    static final SchemaBuilder RECURSIVE_LIST_BUILDER = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.recursion#RecursiveList"));
    static final SchemaBuilder RECURSIVE_MAP_BUILDER = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.recursion#RecursiveMap"));
    static final SchemaBuilder LIST_ATTRIBUTE_VALUE_BUILDER = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.recursion#ListAttributeValue"));
    static final SchemaBuilder MAP_ATTRIBUTE_VALUE_BUILDER = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.recursion#MapAttributeValue"));

    static final Schema BIG_DECIMALS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#BigDecimals"))
        .putMember("member", PreludeSchemas.BIG_DECIMAL)
        .build();

    static final Schema BIG_INTEGERS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#BigIntegers"))
        .putMember("member", PreludeSchemas.BIG_INTEGER)
        .build();

    static final Schema BLOBS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Blobs"))
        .putMember("member", PreludeSchemas.BLOB)
        .build();

    static final Schema BOOLEANS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Booleans"))
        .putMember("member", PreludeSchemas.BOOLEAN)
        .build();

    static final Schema BYTES = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Bytes"))
        .putMember("member", PreludeSchemas.BYTE)
        .build();

    static final Schema DOCS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Docs"))
        .putMember("member", PreludeSchemas.DOCUMENT)
        .build();

    static final Schema DOUBLES = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Doubles"))
        .putMember("member", PreludeSchemas.DOUBLE)
        .build();

    static final Schema ENUMS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Enums"))
        .putMember("member", NestedEnum.SCHEMA)
        .build();

    static final Schema FLOATS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Floats"))
        .putMember("member", PreludeSchemas.FLOAT)
        .build();

    static final Schema INTEGERS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Integers"))
        .putMember("member", PreludeSchemas.INTEGER)
        .build();

    static final Schema INT_ENUMS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#IntEnums"))
        .putMember("member", NestedIntEnum.SCHEMA)
        .build();

    static final Schema LONGS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Longs"))
        .putMember("member", PreludeSchemas.LONG)
        .build();

    static final Schema SHORTS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Shorts"))
        .putMember("member", PreludeSchemas.SHORT)
        .build();

    static final Schema LIST_OF_STRING = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.common#ListOfString"))
        .putMember("member", PreludeSchemas.STRING)
        .build();

    static final Schema STRUCTS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Structs"))
        .putMember("member", NestedStruct.SCHEMA)
        .build();

    static final Schema TIMESTAMPS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Timestamps"))
        .putMember("member", PreludeSchemas.TIMESTAMP)
        .build();

    static final Schema UNIONS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#Unions"))
        .putMember("member", NestedUnion.SCHEMA)
        .build();

    static final Schema LIST_OF_STRING_LIST = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#ListOfStringList"))
        .putMember("member", SharedSchemas.LIST_OF_STRING)
        .build();

    static final Schema LIST_OF_LIST_OF_STRING_LIST = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#ListOfListOfStringList"))
        .putMember("member", SharedSchemas.LIST_OF_STRING_LIST)
        .build();

    static final Schema STRING_STRING_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.common#StringStringMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.STRING)
        .build();

    static final Schema LIST_OF_MAPS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#ListOfMaps"))
        .putMember("member", SharedSchemas.STRING_STRING_MAP)
        .build();

    static final Schema SET_OF_BLOBS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfBlobs"),
        new UniqueItemsTrait())
        .putMember("member", PreludeSchemas.BLOB)
        .build();

    static final Schema SET_OF_BOOLEANS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfBooleans"),
        new UniqueItemsTrait())
        .putMember("member", PreludeSchemas.BOOLEAN)
        .build();

    static final Schema SET_OF_ENUMS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfEnums"),
        new UniqueItemsTrait())
        .putMember("member", NestedEnum.SCHEMA)
        .build();

    static final Schema SET_OF_INT_ENUMS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfIntEnums"),
        new UniqueItemsTrait())
        .putMember("member", NestedIntEnum.SCHEMA)
        .build();

    static final Schema SET_OF_NUMBER = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfNumber"),
        new UniqueItemsTrait())
        .putMember("member", PreludeSchemas.INTEGER)
        .build();

    static final Schema SET_OF_STRINGS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.common#SetOfStrings"),
        new UniqueItemsTrait())
        .putMember("member", PreludeSchemas.STRING)
        .build();

    static final Schema SET_OF_STRING_LIST = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfStringList"),
        new UniqueItemsTrait())
        .putMember("member", SharedSchemas.LIST_OF_STRING)
        .build();

    static final Schema SET_OF_STRING_MAP = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfStringMap"),
        new UniqueItemsTrait())
        .putMember("member", SharedSchemas.STRING_STRING_MAP)
        .build();

    static final Schema SET_OF_STRUCTS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfStructs"),
        new UniqueItemsTrait())
        .putMember("member", NestedStruct.SCHEMA)
        .build();

    static final Schema SET_OF_TIMESTAMPS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfTimestamps"),
        new UniqueItemsTrait())
        .putMember("member", PreludeSchemas.TIMESTAMP)
        .build();

    static final Schema SET_OF_UNIONS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SetOfUnions"),
        new UniqueItemsTrait())
        .putMember("member", NestedUnion.SCHEMA)
        .build();

    static final Schema SPARSE_BIG_DECIMALS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseBigDecimals"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.BIG_DECIMAL)
        .build();

    static final Schema SPARSE_BIG_INTEGERS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseBigIntegers"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.BIG_INTEGER)
        .build();

    static final Schema SPARSE_BLOBS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseBlobs"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.BLOB)
        .build();

    static final Schema SPARSE_BOOLEANS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseBooleans"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.BOOLEAN)
        .build();

    static final Schema SPARSE_BYTES = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseBytes"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.BYTE)
        .build();

    static final Schema SPARSE_DOCS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseDocs"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.DOCUMENT)
        .build();

    static final Schema SPARSE_DOUBLES = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseDoubles"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.DOUBLE)
        .build();

    static final Schema SPARSE_ENUMS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseEnums"),
        new SparseTrait())
        .putMember("member", NestedEnum.SCHEMA)
        .build();

    static final Schema SPARSE_FLOATS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseFloats"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.FLOAT)
        .build();

    static final Schema SPARSE_INTEGERS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseIntegers"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.INTEGER)
        .build();

    static final Schema SPARSE_INT_ENUMS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseIntEnums"),
        new SparseTrait())
        .putMember("member", NestedIntEnum.SCHEMA)
        .build();

    static final Schema SPARSE_LONGS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseLongs"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.LONG)
        .build();

    static final Schema SPARSE_SHORTS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseShorts"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.SHORT)
        .build();

    static final Schema SPARSE_STRINGS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseStrings"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.STRING)
        .build();

    static final Schema SPARSE_STRUCTS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseStructs"),
        new SparseTrait())
        .putMember("member", NestedStruct.SCHEMA)
        .build();

    static final Schema SPARSE_TIMESTAMPS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseTimestamps"),
        new SparseTrait())
        .putMember("member", PreludeSchemas.TIMESTAMP)
        .build();

    static final Schema SPARSE_UNIONS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.lists#SparseUnions"),
        new SparseTrait())
        .putMember("member", NestedUnion.SCHEMA)
        .build();

    static final Schema STRING_BIG_DECIMAL_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringBigDecimalMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BIG_DECIMAL)
        .build();

    static final Schema STRING_BIG_INTEGER_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringBigIntegerMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BIG_INTEGER)
        .build();

    static final Schema STRING_BLOB_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringBlobMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BLOB)
        .build();

    static final Schema STRING_BOOLEAN_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringBooleanMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BOOLEAN)
        .build();

    static final Schema STRING_BYTE_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringByteMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BYTE)
        .build();

    static final Schema STRING_DOUBLE_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringDoubleMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.DOUBLE)
        .build();

    static final Schema STRING_ENUM_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringEnumMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", NestedEnum.SCHEMA)
        .build();

    static final Schema STRING_FLOAT_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringFloatMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.FLOAT)
        .build();

    static final Schema STRING_INTEGER_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringIntegerMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.INTEGER)
        .build();

    static final Schema STRING_INT_ENUM_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringIntEnumMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", NestedIntEnum.SCHEMA)
        .build();

    static final Schema STRING_LONG_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringLongMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.LONG)
        .build();

    static final Schema STRING_SHORT_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringShortMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.SHORT)
        .build();

    static final Schema STRING_STRUCT_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringStructMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", NestedStruct.SCHEMA)
        .build();

    static final Schema STRING_TIMESTAMP_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringTimestampMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.TIMESTAMP)
        .build();

    static final Schema STRING_UNION_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringUnionMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", NestedUnion.SCHEMA)
        .build();

    static final Schema STRING_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.STRING)
        .build();

    static final Schema MAP_LIST = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.maps#MapList"))
        .putMember("member", SharedSchemas.STRING_MAP)
        .build();

    static final Schema MAP_OF_MAP_LIST = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#MapOfMapList"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", SharedSchemas.MAP_LIST)
        .build();

    static final Schema MAP_OF_STRING_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#MapOfStringMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", SharedSchemas.STRING_MAP)
        .build();

    static final Schema MAP_OF_MAP_OF_STRING_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#MapOfMapOfStringMap"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", SharedSchemas.MAP_OF_STRING_MAP)
        .build();

    static final Schema STRING_LIST = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.maps#StringList"))
        .putMember("member", PreludeSchemas.STRING)
        .build();

    static final Schema MAP_OF_STRING_LIST = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#MapOfStringList"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", SharedSchemas.STRING_LIST)
        .build();

    static final Schema SPARSE_STRING_BIG_DECIMAL_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringBigDecimalMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BIG_DECIMAL)
        .build();

    static final Schema SPARSE_STRING_BIG_INTEGER_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringBigIntegerMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BIG_INTEGER)
        .build();

    static final Schema SPARSE_STRING_BLOB_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringBlobMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BLOB)
        .build();

    static final Schema SPARSE_STRING_BOOLEAN_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringBooleanMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BOOLEAN)
        .build();

    static final Schema SPARSE_STRING_BYTE_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringByteMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.BYTE)
        .build();

    static final Schema SPARSE_STRING_DOUBLE_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringDoubleMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.DOUBLE)
        .build();

    static final Schema SPARSE_STRING_ENUM_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringEnumMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", NestedEnum.SCHEMA)
        .build();

    static final Schema SPARSE_STRING_FLOAT_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringFloatMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.FLOAT)
        .build();

    static final Schema SPARSE_STRING_INTEGER_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringIntegerMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.INTEGER)
        .build();

    static final Schema SPARSE_STRING_INT_ENUM_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringIntEnumMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", NestedIntEnum.SCHEMA)
        .build();

    static final Schema SPARSE_STRING_LONG_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringLongMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.LONG)
        .build();

    static final Schema SPARSE_STRING_SHORT_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringShortMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.SHORT)
        .build();

    static final Schema SPARSE_STRING_STRING_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringStringMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.STRING)
        .build();

    static final Schema SPARSE_STRING_STRUCT_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringStructMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", NestedStruct.SCHEMA)
        .build();

    static final Schema SPARSE_STRING_TIMESTAMP_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringTimestampMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.TIMESTAMP)
        .build();

    static final Schema SPARSE_STRING_UNION_MAP = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.maps#SparseStringUnionMap"),
        new SparseTrait())
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", NestedUnion.SCHEMA)
        .build();

    static final Schema STREAMING_BLOB = Schema.createBlob(ShapeId.from("smithy.java.codegen.test.common#StreamingBlob"));

    static final Schema LIST_OF_STRINGS = Schema.listBuilder(ShapeId.from("smithy.java.codegen.test.structures#ListOfStrings"))
        .putMember("member", PreludeSchemas.STRING)
        .build();

    static final Schema MAP_STRING_STRING = Schema.mapBuilder(ShapeId.from("smithy.java.codegen.test.structures#MapStringString"))
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", PreludeSchemas.STRING)
        .build();

    static final Schema RECURSIVE_LIST = RECURSIVE_LIST_BUILDER
        .putMember("member", IntermediateListStructure.SCHEMA_BUILDER)
        .build();

    static final Schema RECURSIVE_MAP = RECURSIVE_MAP_BUILDER
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", IntermediateMapStructure.SCHEMA_BUILDER)
        .build();

    static final Schema LIST_ATTRIBUTE_VALUE = LIST_ATTRIBUTE_VALUE_BUILDER
        .putMember("member", AttributeValue.SCHEMA_BUILDER)
        .build();

    static final Schema MAP_ATTRIBUTE_VALUE = MAP_ATTRIBUTE_VALUE_BUILDER
        .putMember("key", PreludeSchemas.STRING)
        .putMember("value", AttributeValue.SCHEMA_BUILDER)
        .build();

    private SharedSchemas() {}
}

