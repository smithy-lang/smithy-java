$version: "2"

namespace smithy.java.codegen.test.lists

use smithy.java.codegen.test.common#ListOfString
use smithy.java.codegen.test.common#NestedEnum
use smithy.java.codegen.test.common#NestedIntEnum
use smithy.java.codegen.test.common#NestedStruct
use smithy.java.codegen.test.common#NestedUnion

operation ListAllTypes {
    input := {
        listOfBoolean: Booleans
        listOfBigDecimal: BigDecimals
        listOfBigInteger: BigIntegers
        listOfByte: Bytes
        listOfDouble: Doubles
        listOfFloat: Floats
        listOfInteger: Integers
        listOfLong: Longs
        listOfShort: Shorts
        listOfString: ListOfString
        listOfBlobs: Blobs
        listOfTimestamps: Timestamps
        listOfUnion: Unions
        listOfEnum: Enums
        listOfIntEnum: IntEnums
        listOfStruct: Structs
    }
}

@private
list Booleans {
    member: Boolean
}

@private
list BigDecimals {
    member: BigDecimal
}

@private
list BigIntegers {
    member: BigInteger
}

@private
list Bytes {
    member: Byte
}

@private
list Doubles {
    member: Double
}

@private
list Floats {
    member: Float
}

@private
list Integers {
    member: Integer
}

@private
list Longs {
    member: Long
}

@private
list Shorts {
    member: Short
}

@private
list Blobs {
    member: Blob
}

@private
list Timestamps {
    member: Timestamp
}

@private
list Unions {
    member: NestedUnion
}

@private
list Enums {
    member: NestedEnum
}

@private
list IntEnums {
    member: NestedIntEnum
}

@private
list Structs {
    member: NestedStruct
}
