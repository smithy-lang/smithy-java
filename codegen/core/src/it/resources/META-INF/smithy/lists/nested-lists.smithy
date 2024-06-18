$version: "2"

namespace smithy.java.codegen.test.lists

use smithy.java.codegen.test.common#ListOfString
use smithy.java.codegen.test.common#StringStringMap

operation NestedLists {
    input := {
        listOfLists: ListOfStringList
        listOfListOfList: ListOfListOfStringList
        listOfMaps: ListOfMaps
    }
}

@private
list ListOfListOfStringList {
    member: ListOfStringList
}

@private
list ListOfStringList {
    member: ListOfString
}

@private
list ListOfMaps {
    member: StringStringMap
}
