

package io.smithy.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 * Defines shared shapes across the model package that are not part of another code-generated type.
 */
final class SharedSchemas {

    static final Schema TIMESTAMPS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Timestamps")
        .members(Schema.memberBuilder("member", PreludeSchemas.TIMESTAMP))
        .build();

    static final Schema SPARSE_BIG_DECIMALS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseBigDecimals")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.BIG_DECIMAL)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema STRING_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.STRING)
        )
        .build();

    static final Schema SET_OF_BOOLEANS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SetOfBooleans")
        .traits(
            new UniqueItemsTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.BOOLEAN)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final Schema SPARSE_STRING_INTEGER_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringIntegerMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.INTEGER)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema SPARSE_STRUCTS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseStructs")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", NestedStruct.SCHEMA)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema MAP_LIST;
    static final Schema SPARSE_STRING_DOUBLE_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringDoubleMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.DOUBLE)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema DOUBLES = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Doubles")
        .members(Schema.memberBuilder("member", PreludeSchemas.DOUBLE))
        .build();

    static final Schema SET_OF_STRING_LIST;
    static final Schema STRING_BOOLEAN_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringBooleanMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.BOOLEAN)
        )
        .build();

    static final Schema BLOBS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Blobs")
        .members(Schema.memberBuilder("member", PreludeSchemas.BLOB))
        .build();

    static final Schema MAP_STRING_STRING = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.structures#MapStringString")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.STRING)
        )
        .build();

    static final Schema LIST_OF_LIST_OF_STRING_LIST;
    static final Schema SHORTS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Shorts")
        .members(Schema.memberBuilder("member", PreludeSchemas.SHORT))
        .build();

    static final Schema SPARSE_STRING_UNION_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringUnionMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", NestedUnion.SCHEMA)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema ENUMS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Enums")
        .members(Schema.memberBuilder("member", NestedEnum.SCHEMA))
        .build();

    static final Schema STRING_LIST = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.maps#StringList")
        .members(Schema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final Schema SPARSE_STRING_SHORT_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringShortMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.SHORT)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema BYTES = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Bytes")
        .members(Schema.memberBuilder("member", PreludeSchemas.BYTE))
        .build();

    static final Schema FLOATS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Floats")
        .members(Schema.memberBuilder("member", PreludeSchemas.FLOAT))
        .build();

    static final Schema STRING_BLOB_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringBlobMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.BLOB)
        )
        .build();

    static final Schema STRING_DOUBLE_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringDoubleMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.DOUBLE)
        )
        .build();

    static final Schema SPARSE_STRING_ENUM_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringEnumMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", NestedEnum.SCHEMA)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema UNIONS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Unions")
        .members(Schema.memberBuilder("member", NestedUnion.SCHEMA))
        .build();

    static final Schema BOOLEANS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Booleans")
        .members(Schema.memberBuilder("member", PreludeSchemas.BOOLEAN))
        .build();

    static final Schema SPARSE_STRING_INT_ENUM_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringIntEnumMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", NestedIntEnum.SCHEMA)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema SPARSE_STRING_BOOLEAN_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringBooleanMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.BOOLEAN)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema STRING_UNION_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringUnionMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", NestedUnion.SCHEMA)
        )
        .build();

    static final Schema SPARSE_STRING_STRUCT_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringStructMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", NestedStruct.SCHEMA)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema STRING_INTEGER_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringIntegerMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.INTEGER)
        )
        .build();

    static final Schema LONGS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Longs")
        .members(Schema.memberBuilder("member", PreludeSchemas.LONG))
        .build();

    static final Schema STRING_ENUM_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringEnumMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", NestedEnum.SCHEMA)
        )
        .build();

    static final Schema STRUCTS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Structs")
        .members(Schema.memberBuilder("member", NestedStruct.SCHEMA))
        .build();

    static final Schema SPARSE_STRING_TIMESTAMP_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringTimestampMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.TIMESTAMP)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema BIG_DECIMALS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#BigDecimals")
        .members(Schema.memberBuilder("member", PreludeSchemas.BIG_DECIMAL))
        .build();

    static final Schema SET_OF_STRINGS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.common#SetOfStrings")
        .traits(
            new UniqueItemsTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.STRING)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final Schema SPARSE_LONGS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseLongs")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.LONG)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema MAP_OF_MAP_LIST;
    static final Schema SPARSE_STRING_BIG_DECIMAL_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringBigDecimalMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.BIG_DECIMAL)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema SPARSE_BLOBS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseBlobs")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.BLOB)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema SET_OF_UNIONS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SetOfUnions")
        .traits(
            new UniqueItemsTrait()
        )
        .members(Schema.memberBuilder("member", NestedUnion.SCHEMA)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final Schema STRING_BIG_DECIMAL_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringBigDecimalMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.BIG_DECIMAL)
        )
        .build();

    static final Schema BIG_INTEGERS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#BigIntegers")
        .members(Schema.memberBuilder("member", PreludeSchemas.BIG_INTEGER))
        .build();

    static final Schema SET_OF_STRUCTS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SetOfStructs")
        .traits(
            new UniqueItemsTrait()
        )
        .members(Schema.memberBuilder("member", NestedStruct.SCHEMA)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final Schema INT_ENUMS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#IntEnums")
        .members(Schema.memberBuilder("member", NestedIntEnum.SCHEMA))
        .build();

    static final Schema SPARSE_BOOLEANS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseBooleans")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.BOOLEAN)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema LIST_OF_STRING_LIST;
    static final Schema STRING_INT_ENUM_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringIntEnumMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", NestedIntEnum.SCHEMA)
        )
        .build();

    static final Schema LIST_OF_STRING = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.common#ListOfString")
        .members(Schema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final Schema SPARSE_STRING_BYTE_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringByteMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.BYTE)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema SPARSE_BIG_INTEGERS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseBigIntegers")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.BIG_INTEGER)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema STREAMING_BLOB = Schema.builder()
        .type(ShapeType.BLOB)
        .id("smithy.java.codegen.test.common#StreamingBlob")
        .build();

    static final Schema STRING_LONG_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringLongMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.LONG)
        )
        .build();

    static final Schema SET_OF_STRING_MAP;
    static final Schema SPARSE_STRING_BIG_INTEGER_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringBigIntegerMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.BIG_INTEGER)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema LIST_OF_STRINGS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.structures#ListOfStrings")
        .members(Schema.memberBuilder("member", PreludeSchemas.STRING))
        .build();

    static final Schema SET_OF_TIMESTAMPS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SetOfTimestamps")
        .traits(
            new UniqueItemsTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.TIMESTAMP)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final Schema SPARSE_STRING_FLOAT_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringFloatMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.FLOAT)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema SET_OF_BLOBS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SetOfBlobs")
        .traits(
            new UniqueItemsTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.BLOB)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final Schema SET_OF_ENUMS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SetOfEnums")
        .traits(
            new UniqueItemsTrait()
        )
        .members(Schema.memberBuilder("member", NestedEnum.SCHEMA)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final Schema SPARSE_TIMESTAMPS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseTimestamps")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.TIMESTAMP)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema STRING_BIG_INTEGER_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringBigIntegerMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.BIG_INTEGER)
        )
        .build();

    static final Schema SPARSE_SHORTS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseShorts")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.SHORT)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema SPARSE_STRING_STRING_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringStringMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema SPARSE_FLOATS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseFloats")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.FLOAT)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema STRING_STRUCT_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringStructMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", NestedStruct.SCHEMA)
        )
        .build();

    static final Schema INTEGERS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Integers")
        .members(Schema.memberBuilder("member", PreludeSchemas.INTEGER))
        .build();

    static final Schema SPARSE_INT_ENUMS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseIntEnums")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", NestedIntEnum.SCHEMA)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema MAP_OF_MAP_OF_STRING_MAP;
    static final Schema STRING_FLOAT_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringFloatMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.FLOAT)
        )
        .build();

    static final Schema DOCS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#Docs")
        .members(Schema.memberBuilder("member", PreludeSchemas.DOCUMENT))
        .build();

    static final Schema SET_OF_INT_ENUMS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SetOfIntEnums")
        .traits(
            new UniqueItemsTrait()
        )
        .members(Schema.memberBuilder("member", NestedIntEnum.SCHEMA)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final Schema SPARSE_DOUBLES = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseDoubles")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.DOUBLE)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema SPARSE_UNIONS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseUnions")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", NestedUnion.SCHEMA)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema LIST_OF_MAPS;
    static final Schema SET_OF_NUMBER = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SetOfNumber")
        .traits(
            new UniqueItemsTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.INTEGER)
        .traits(
            new UniqueItemsTrait()
        ))
        .build();

    static final Schema SPARSE_BYTES = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseBytes")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.BYTE)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema STRING_TIMESTAMP_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringTimestampMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.TIMESTAMP)
        )
        .build();

    static final Schema SPARSE_STRING_BLOB_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringBlobMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.BLOB)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema STRING_STRING_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.common#StringStringMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.STRING)
        )
        .build();

    static final Schema SPARSE_ENUMS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseEnums")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", NestedEnum.SCHEMA)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema MAP_OF_STRING_MAP;
    static final Schema STRING_BYTE_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringByteMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.BYTE)
        )
        .build();

    static final Schema MAP_OF_STRING_LIST;
    static final Schema STRING_SHORT_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#StringShortMap")
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING),
                Schema.memberBuilder("value", PreludeSchemas.SHORT)
        )
        .build();

    static final Schema SPARSE_STRING_LONG_MAP = Schema.builder()
        .type(ShapeType.MAP)
        .id("smithy.java.codegen.test.maps#SparseStringLongMap")
        .traits(
            new SparseTrait()
        )
        .members(
                Schema.memberBuilder("key", PreludeSchemas.STRING)
                    .traits(
                        new SparseTrait()
                    ),
                Schema.memberBuilder("value", PreludeSchemas.LONG)
                    .traits(
                        new SparseTrait()
                    )
        )
        .build();

    static final Schema SPARSE_DOCS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseDocs")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.DOCUMENT)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema SPARSE_INTEGERS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseIntegers")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.INTEGER)
        .traits(
            new SparseTrait()
        ))
        .build();

    static final Schema SPARSE_STRINGS = Schema.builder()
        .type(ShapeType.LIST)
        .id("smithy.java.codegen.test.lists#SparseStrings")
        .traits(
            new SparseTrait()
        )
        .members(Schema.memberBuilder("member", PreludeSchemas.STRING)
        .traits(
            new SparseTrait()
        ))
        .build();

    static {
        LIST_OF_STRING_LIST = Schema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.lists#ListOfStringList")
            .members(Schema.memberBuilder("member", SharedSchemas.LIST_OF_STRING))
            .build();

        MAP_OF_STRING_MAP = Schema.builder()
            .type(ShapeType.MAP)
            .id("smithy.java.codegen.test.maps#MapOfStringMap")
            .members(
                    Schema.memberBuilder("key", PreludeSchemas.STRING),
                    Schema.memberBuilder("value", SharedSchemas.STRING_MAP)
            )
            .build();

        LIST_OF_MAPS = Schema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.lists#ListOfMaps")
            .members(Schema.memberBuilder("member", SharedSchemas.STRING_STRING_MAP))
            .build();

        MAP_LIST = Schema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.maps#MapList")
            .members(Schema.memberBuilder("member", SharedSchemas.STRING_MAP))
            .build();

        MAP_OF_STRING_LIST = Schema.builder()
            .type(ShapeType.MAP)
            .id("smithy.java.codegen.test.maps#MapOfStringList")
            .members(
                    Schema.memberBuilder("key", PreludeSchemas.STRING),
                    Schema.memberBuilder("value", SharedSchemas.STRING_LIST)
            )
            .build();

        SET_OF_STRING_MAP = Schema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.lists#SetOfStringMap")
            .traits(
                new UniqueItemsTrait()
            )
            .members(Schema.memberBuilder("member", SharedSchemas.STRING_STRING_MAP)
            .traits(
                new UniqueItemsTrait()
            ))
            .build();

        SET_OF_STRING_LIST = Schema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.lists#SetOfStringList")
            .traits(
                new UniqueItemsTrait()
            )
            .members(Schema.memberBuilder("member", SharedSchemas.LIST_OF_STRING)
            .traits(
                new UniqueItemsTrait()
            ))
            .build();

        MAP_OF_MAP_OF_STRING_MAP = Schema.builder()
            .type(ShapeType.MAP)
            .id("smithy.java.codegen.test.maps#MapOfMapOfStringMap")
            .members(
                    Schema.memberBuilder("key", PreludeSchemas.STRING),
                    Schema.memberBuilder("value", SharedSchemas.MAP_OF_STRING_MAP)
            )
            .build();

        LIST_OF_LIST_OF_STRING_LIST = Schema.builder()
            .type(ShapeType.LIST)
            .id("smithy.java.codegen.test.lists#ListOfListOfStringList")
            .members(Schema.memberBuilder("member", SharedSchemas.LIST_OF_STRING_LIST))
            .build();

        MAP_OF_MAP_LIST = Schema.builder()
            .type(ShapeType.MAP)
            .id("smithy.java.codegen.test.maps#MapOfMapList")
            .members(
                    Schema.memberBuilder("key", PreludeSchemas.STRING),
                    Schema.memberBuilder("value", SharedSchemas.MAP_LIST)
            )
            .build();

    }

    private SharedSchemas() {}
}

