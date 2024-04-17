$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Lists {
    input := {
        /// Required list with no default value
        @required
        requiredList: ListOfStrings

        /// List with a default value. Lists can only ever have empty defaults.
        @default([])
        listWithDefault: ListOfStrings

        /// Optional list
        optionalList: ListOfStrings
    }
}

@private
list ListOfStrings {
    member: String
}
