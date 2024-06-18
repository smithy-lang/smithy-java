$version: "2"

namespace smithy.java.codegen.test.structures

operation BooleanMembers {
    input := {
        @required
        requiredBoolean: Boolean

        @default(true)
        defaultBoolean: Boolean

        optionalBoolean: Boolean
    }
}
