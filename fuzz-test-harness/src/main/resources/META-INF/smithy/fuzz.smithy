$version: "2"

namespace smithy.fuzz.test

/// Simple scalar value structures for fuzzing
structure BlobValue {
    value: Blob
}

structure BooleanValue {
    value: Boolean
}

structure ByteValue {
    value: Byte
}

structure TimestampValue {
    value: Timestamp
}

structure DoubleValue {
    value: Double
}

structure FloatValue {
    value: Float
}

structure IntegerValue {
    value: Integer
}

structure LongValue {
    value: Long
}

structure ShortValue {
    value: Short
}

structure StringValue {
    value: String
}

structure BigIntegerValue {
    value: BigInteger
}

structure BigDecimalValue {
    value: BigDecimal
}

structure DocumentValue {
    value: Document
}

/// List value structures for fuzzing
structure BlobListValue {
    value: BlobList
}

structure BooleanListValue {
    value: BooleanList
}

structure ByteListValue {
    value: ByteList
}

structure TimestampListValue {
    value: TimestampList
}

structure DoubleListValue {
    value: DoubleList
}

structure FloatListValue {
    value: FloatList
}

structure IntegerListValue {
    value: IntegerList
}

structure LongListValue {
    value: LongList
}

structure ShortListValue {
    value: ShortList
}

structure StringListValue {
    value: StringList
}

structure BigIntegerListValue {
    value: BigIntegerList
}

structure BigDecimalListValue {
    value: BigDecimalList
}

structure DocumentListValue {
    value: DocumentList
}

/// Map value structures for fuzzing
structure StringBlobMapValue {
    value: StringBlobMap
}

structure StringBooleanMapValue {
    value: StringBooleanMap
}

structure StringByteMapValue {
    value: StringByteMap
}

structure StringTimestampMapValue {
    value: StringTimestampMap
}

structure StringDoubleMapValue {
    value: StringDoubleMap
}

structure StringFloatMapValue {
    value: StringFloatMap
}

structure StringIntegerMapValue {
    value: StringIntegerMap
}

structure StringLongMapValue {
    value: StringLongMap
}

structure StringShortMapValue {
    value: StringShortMap
}

structure StringStringMapValue {
    value: StringStringMap
}

structure StringBigIntegerMapValue {
    value: StringBigIntegerMap
}

structure StringBigDecimalMapValue {
    value: StringBigDecimalMap
}

structure StringDocumentMapValue {
    value: StringDocumentMap
}

/// Nested structure for complex fuzzing
structure NestedStructureValue {
    stringField: String
    intField: Integer
    listField: StringList
    mapField: StringIntegerMap
    nestedStruct: SimpleNestedStruct
}

structure SimpleNestedStruct {
    field1: String
    field2: Integer
}

/// Union for fuzzing discriminated unions
union TestUnion {
    stringVariant: String
    intVariant: Integer
    structVariant: SimpleNestedStruct
}

structure UnionValue {
    value: TestUnion
}

/// List type definitions
list BlobList {
    member: Blob
}

list BooleanList {
    member: Boolean
}

list ByteList {
    member: Byte
}

list TimestampList {
    member: Timestamp
}

list DoubleList {
    member: Double
}

list FloatList {
    member: Float
}

list IntegerList {
    member: Integer
}

list LongList {
    member: Long
}

list ShortList {
    member: Short
}

list StringList {
    member: String
}

list BigIntegerList {
    member: BigInteger
}

list BigDecimalList {
    member: BigDecimal
}

list DocumentList {
    member: Document
}

/// Map type definitions
map StringBlobMap {
    key: String
    value: Blob
}

map StringBooleanMap {
    key: String
    value: Boolean
}

map StringByteMap {
    key: String
    value: Byte
}

map StringTimestampMap {
    key: String
    value: Timestamp
}

map StringDoubleMap {
    key: String
    value: Double
}

map StringFloatMap {
    key: String
    value: Float
}

map StringIntegerMap {
    key: String
    value: Integer
}

map StringLongMap {
    key: String
    value: Long
}

map StringShortMap {
    key: String
    value: Short
}

map StringStringMap {
    key: String
    value: String
}

map StringBigIntegerMap {
    key: String
    value: BigInteger
}

map StringBigDecimalMap {
    key: String
    value: BigDecimal
}

map StringDocumentMap {
    key: String
    value: Document
}
