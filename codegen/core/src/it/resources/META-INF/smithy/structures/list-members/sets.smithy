$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Sets {
    input := {
        /// Required set with no default value
        @required
        requiredList: SetOfStrings

        /// Set with a default value. Sets can only ever have empty defaults.
        @default([])
        listWithDefault: SetOfStrings

        /// Optional set
        optionalList: SetOfStrings
    }
}

@private
@uniqueItems
list SetOfStrings {
    member: String
}
