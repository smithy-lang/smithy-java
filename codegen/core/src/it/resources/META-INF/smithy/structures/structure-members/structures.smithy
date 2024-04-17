$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Structures {
    input := {
        @required
        requiredStruct: Nested

        optionalStruct: Nested
    }
}

@private
structure Nested {
    fieldA: String
    fieldB: Integer
}
