$version: "2"

namespace smithy.java.codegen.test.structures

use smithy.java.codegen.test.common#SetOfStrings

operation SetMembers {
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
