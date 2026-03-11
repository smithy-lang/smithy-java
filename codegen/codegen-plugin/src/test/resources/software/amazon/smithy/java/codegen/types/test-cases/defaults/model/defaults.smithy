$version: "2"

namespace smithy.java.codegen.test.structures

structure DefaultStructure {
    @default(true)
    boolean: Boolean
    @default(1e309)
    bigDecimal: BigDecimal
    @default(1.3)
    bigDecimalWithDoubleDefault: BigDecimal
    @default(5)
    bigDecimalWithLongDefault: BigDecimal
    @default(123456789123456789123456789123456789123456789123456789)
    bigInteger: BigInteger
    @default(1)
    bigIntegerWithLongDefault: BigInteger
    @default(1)
    byte: Byte
    @default(1.0)
    double: Double
    @default(1.0)
    float: Float
    @default(1)
    integer: Integer
    @default(1)
    long: Long
    @default(1)
    short: Short
    @default("default")
    string: String
    @default("YmxvYg==")
    blob: Blob
    @default("c3RyZWFtaW5n")
    streamingBlob: StreamingBlob
    // Documents can be bool, string, numbers, an empty list, or an empty map.
    // see: https://smithy.io/2.0/spec/type-refinement-traits.html#default-value-constraints
    @default(true)
    boolDoc: Document
    @default("string")
    stringDoc: Document
    @default(1)
    numberDoc: Document
    @default(1.2)
    floatingPointnumberDoc: Document
    @default([])
    listDoc: Document
    @default({})
    mapDoc: Document
    @default([])
    list: ListOfString
    @default({})
    map: StringStringMap
    @default("1985-04-12T23:20:50.52Z")
    timestamp: Timestamp
    @default("A")
    enum: NestedEnum
    @default(1)
    intEnum: NestedIntEnum
}


intEnum NestedIntEnum {
    A = 1
    B = 2
}

enum NestedEnum {
    A
    B
}

list ListOfString {
    member: String
}

map StringStringMap {
    key: String
    value: String
}

@streaming
blob StreamingBlob
