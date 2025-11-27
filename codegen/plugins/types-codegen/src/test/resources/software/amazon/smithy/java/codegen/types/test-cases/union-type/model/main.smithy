$version: "2"

namespace smithy.test

union UnionType {
    blobValue: Blob
    booleanValue: Boolean
    listValue: ListOfString
    mapValue: StringStringMap
    bigDecimalValue: BigDecimal
    bigIntegerValue: BigInteger
    byteValue: Byte
    doubleValue: Double
    floatValue: Float
    integerValue: Integer
    longValue: Long
    shortValue: Short
    stringValue: String
    structureValue: NestedStruct
    timestampValue: Timestamp
    unionValue: NestedUnion
    enumValue: NestedEnum
    intEnumValue: NestedIntEnum
    unitValue: Unit
}

list ListOfString {
    member: String
}

map StringStringMap {
    key: String
    value: String
}

structure NestedStruct {
    fieldA: String
    fieldB: Integer
}

union NestedUnion {
    a: String
    b: Integer
}

enum NestedEnum {
    A
    B
}

intEnum NestedIntEnum {
    A = 1
    B = 2
}


