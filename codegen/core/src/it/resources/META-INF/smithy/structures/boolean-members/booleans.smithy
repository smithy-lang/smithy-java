$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Booleans {
    input := {
        @required
        requiredBoolean: Boolean

        @default(true)
        defaultBoolean: Boolean

        optionalBoolean: Boolean
    }
}
