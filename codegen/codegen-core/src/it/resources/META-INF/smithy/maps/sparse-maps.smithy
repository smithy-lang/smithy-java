$version: "2"

namespace smithy.java.codegen.test.maps

use smithy.java.codegen.test.common#NestedEnum
use smithy.java.codegen.test.common#NestedIntEnum
use smithy.java.codegen.test.common#NestedStruct
use smithy.java.codegen.test.common#NestedUnion

operation SparseMaps {
    input := {
        stringBooleanMap: SparseStringBooleanMap
        stringBigDecimalMap: SparseStringBigDecimalMap
        stringBigIntegerMap: SparseStringBigIntegerMap
        stringByteMap: SparseStringByteMap
        stringDoubleMap: SparseStringDoubleMap
        stringFloatMap: SparseStringFloatMap
        stringIntegerMap: SparseStringIntegerMap
        stringLongMap: SparseStringLongMap
        stringShortMap: SparseStringShortMap
        stringStringMap: SparseStringStringMap
        stringBlobMap: SparseStringBlobMap
        stringTimestampMap: SparseStringTimestampMap
        stringUnionMap: SparseStringUnionMap
        stringEnumMap: SparseStringEnumMap
        stringIntEnumMap: SparseStringIntEnumMap
        stringStructMap: SparseStringStructMap
    }
}

@private
@sparse
map SparseStringBooleanMap {
    key: String
    value: Boolean
}

@private
@sparse
map SparseStringBigDecimalMap {
    key: String
    value: BigDecimal
}

@private
@sparse
map SparseStringBigIntegerMap {
    key: String
    value: BigInteger
}

@private
@sparse
map SparseStringByteMap {
    key: String
    value: Byte
}

@private
@sparse
map SparseStringDoubleMap {
    key: String
    value: Double
}

@private
@sparse
map SparseStringFloatMap {
    key: String
    value: Float
}

@private
@sparse
map SparseStringIntegerMap {
    key: String
    value: Integer
}

@private
@sparse
map SparseStringLongMap {
    key: String
    value: Long
}

@private
@sparse
map SparseStringShortMap {
    key: String
    value: Short
}

@private
@sparse
map SparseStringStringMap {
    key: String
    value: String
}

@private
@sparse
map SparseStringBlobMap {
    key: String
    value: Blob
}

@private
@sparse
map SparseStringTimestampMap {
    key: String
    value: Timestamp
}

@private
@sparse
map SparseStringUnionMap {
    key: String
    value: NestedUnion
}

@private
@sparse
map SparseStringEnumMap {
    key: String
    value: NestedEnum
}

@private
@sparse
map SparseStringIntEnumMap {
    key: String
    value: NestedIntEnum
}

@private
@sparse
map SparseStringStructMap {
    key: String
    value: NestedStruct
}
