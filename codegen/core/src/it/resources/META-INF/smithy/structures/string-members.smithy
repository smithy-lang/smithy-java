$version: "2"

namespace smithy.java.codegen.test.structures

operation StringMembers {
    input := {
        @required
        requiredString: String

        @default("default")
        defaultString: String

        optionalString: String
    }
}
