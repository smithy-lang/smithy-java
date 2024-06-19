$version: "2"

namespace smithy.java.codegen.test.lists

use smithy.java.codegen.test.common#NestedEnum
use smithy.java.codegen.test.common#NestedIntEnum
use smithy.java.codegen.test.common#NestedStruct
use smithy.java.codegen.test.common#NestedUnion

operation SparseLists {
    input := {
        listOfBooleans: SparseBooleans
        listOfBigDecimal: SparseBigDecimals
        listOfBigInteger: SparseBigIntegers
        listOfByte: SparseBytes
        listOfDouble: SparseDoubles
        listOfFloat: SparseFloats
        listOfInteger: SparseIntegers
        listOfLong: SparseLongs
        listOfShort: SparseShorts
        listOfString: SparseStrings
        listOfBlobs: SparseBlobs
        listOfTimestamps: SparseTimestamps
        listOfUnion: SparseUnions
        listOfEnum: SparseEnums
        listOfIntEnum: SparseIntEnums
        listOfStruct: SparseStructs
        listOfDocuments: SparseDocs
    }
}

@private
@sparse
list SparseBooleans {
    member: Boolean
}

@private
@sparse
list SparseBigDecimals {
    member: BigDecimal
}

@private
@sparse
list SparseBigIntegers {
    member: BigInteger
}

@private
@sparse
list SparseBytes {
    member: Byte
}

@private
@sparse
list SparseDoubles {
    member: Double
}

@private
@sparse
list SparseFloats {
    member: Float
}

@private
@sparse
list SparseIntegers {
    member: Integer
}

@private
@sparse
list SparseLongs {
    member: Long
}

@private
@sparse
list SparseShorts {
    member: Short
}

@private
@sparse
list SparseBlobs {
    member: Blob
}

@private
@sparse
list SparseStrings {
    member: String
}

@private
@sparse
list SparseTimestamps {
    member: Timestamp
}

@private
@sparse
list SparseUnions {
    member: NestedUnion
}

@private
@sparse
list SparseEnums {
    member: NestedEnum
}

@private
@sparse
list SparseIntEnums {
    member: NestedIntEnum
}

@private
@sparse
list SparseStructs {
    member: NestedStruct
}

@private
@sparse
list SparseDocs {
    member: Document
}

