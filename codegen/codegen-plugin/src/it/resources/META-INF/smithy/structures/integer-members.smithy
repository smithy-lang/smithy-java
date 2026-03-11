$version: "2"

namespace smithy.java.codegen.test.structures

operation IntegerMembers {
    input := {
        @required
        requiredInt: Integer

        optionalInt: Integer

        @default(1)
        defaultInt: Integer
    }
}
