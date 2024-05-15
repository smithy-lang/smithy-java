$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Strings {
    input := {
        @required
        requiredString: String

        @default("default")
        defaultString: String

        optionalString: String
    }
}

