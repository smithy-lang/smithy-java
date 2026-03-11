$version: "2"

namespace smithy.java.codegen.test.lists

use smithy.java.codegen.test.common#ListOfString
use smithy.java.codegen.test.common#NestedEnum
use smithy.java.codegen.test.common#NestedIntEnum
use smithy.java.codegen.test.common#NestedStruct
use smithy.java.codegen.test.common#NestedUnion
use smithy.java.codegen.test.common#SetOfStrings
use smithy.java.codegen.test.common#StringStringMap

operation SetsAllTypes {
    input := {
        setOfBoolean: SetOfBooleans
        setOfNumber: SetOfNumber
        setOfString: SetOfStrings
        setOfBlobs: SetOfBlobs
        setOfTimestamps: SetOfTimestamps
        setOfUnion: SetOfUnions
        setOfEnum: SetOfEnums
        setOfIntEnum: SetOfIntEnums
        setOfStruct: SetOfStructs
        setOfStringList: SetOfStringList
        setOfStringMap: SetOfStringMap
    }
}

@private
@uniqueItems
list SetOfBooleans {
    member: Boolean
}

@private
@uniqueItems
list SetOfNumber {
    member: Integer
}

@private
@uniqueItems
list SetOfBlobs {
    member: Blob
}

@private
@uniqueItems
list SetOfTimestamps {
    member: Timestamp
}

@private
@uniqueItems
list SetOfUnions {
    member: NestedUnion
}

@private
@uniqueItems
list SetOfEnums {
    member: NestedEnum
}

@private
@uniqueItems
list SetOfIntEnums {
    member: NestedIntEnum
}

@private
@uniqueItems
list SetOfStructs {
    member: NestedStruct
}

@private
@uniqueItems
list SetOfStringList {
    member: ListOfString
}

@private
@uniqueItems
list SetOfStringMap {
    member: StringStringMap
}
