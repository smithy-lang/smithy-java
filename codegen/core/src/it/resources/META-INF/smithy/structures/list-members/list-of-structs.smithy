$version: "2"

namespace smithy.java.codegen.test.structures.members

operation ListWithStructs {
    input := {
        listOfStructs: ListOfStruct
    }
}

@private
list ListOfStruct {
    member: Nested
}

@private
structure Nested {
    fieldA: String
    fieldB: Integer
}
