$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Unions {
    input := {
        @required
        requiredUnion: UnionType

        optionalUnion: UnionType
    }
}

@private
union UnionType {
    blobValue: Blob
    booleanValue: Boolean
    listValue: ListOfStrings
    mapValue: StringMap
    bigDecimalValue: BigDecimal
    bigIntegerValue: BigInteger
    byteValue: Byte
    doubleValue: Double
    floatValue: Float
    integerValue: Integer
    longValue: Long
    shortValue: Short
    stringValue: String
    structureValue: Struct
    timestampValue: Timestamp
    unionValue: OtherUnion
}

@private
structure Struct {
    field: String
}

@private
union OtherUnion {
    str: String
    int: Integer
}

