$version: "2"

namespace smithy.java.codegen.test.structures.members

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
list ListOfString {
    member: String
}

@private
list ListOfMaps {
    member: StringStringMap
}

@private
map StringStringMap {
    key: String
    value: String
}
