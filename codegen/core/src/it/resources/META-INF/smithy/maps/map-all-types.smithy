$version: "2"

namespace smithy.java.codegen.test.maps

use smithy.java.codegen.test.common#NestedEnum
use smithy.java.codegen.test.common#NestedIntEnum
use smithy.java.codegen.test.common#NestedStruct
use smithy.java.codegen.test.common#NestedUnion
use smithy.java.codegen.test.common#StringStringMap

operation MapAllTypes {
    input := {
        stringBooleanMap: StringBooleanMap
        stringBigDecimalMap: StringBigDecimalMap
        stringBigIntegerMap: StringBigIntegerMap
        stringByteMap: StringByteMap
        stringDoubleMap: StringDoubleMap
        stringFloatMap: StringFloatMap
        stringIntegerMap: StringIntegerMap
        stringLongMap: StringLongMap
        stringShortMap: StringShortMap
        stringStringMap: StringStringMap
        stringBlobMap: StringBlobMap
        stringTimestampMap: StringTimestampMap
        stringUnionMap: StringUnionMap
        stringEnumMap: StringEnumMap
        stringIntEnumMap: StringIntEnumMap
        stringStructMap: StringStructMap
    }
}

@private
map StringBooleanMap {
    key: String
    value: Boolean
}

@private
map StringBigDecimalMap {
    key: String
    value: BigDecimal
}

@private
map StringBigIntegerMap {
    key: String
    value: BigInteger
}

@private
map StringByteMap {
    key: String
    value: Byte
}

@private
map StringDoubleMap {
    key: String
    value: Double
}

@private
map StringFloatMap {
    key: String
    value: Float
}

@private
map StringIntegerMap {
    key: String
    value: Integer
}

@private
map StringLongMap {
    key: String
    value: Long
}

@private
map StringShortMap {
    key: String
    value: Short
}

@private
map StringBlobMap {
    key: String
    value: Blob
}

@private
map StringTimestampMap {
    key: String
    value: Timestamp
}

@private
map StringUnionMap {
    key: String
    value: NestedUnion
}

@private
map StringEnumMap {
    key: String
    value: NestedEnum
}

@private
map StringIntEnumMap {
    key: String
    value: NestedIntEnum
}

@private
map StringStructMap {
    key: String
    value: NestedStruct
}
