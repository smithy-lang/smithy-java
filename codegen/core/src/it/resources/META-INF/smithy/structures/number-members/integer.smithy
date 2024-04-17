$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Integers {
    input := {
        @required
        requiredInt: Integer

        optionalInt: Integer

        @default(1)
        defaultInt: Integer
    }
}
