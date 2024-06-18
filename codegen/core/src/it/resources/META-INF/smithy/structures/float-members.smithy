$version: "2"

namespace smithy.java.codegen.test.structures

operation FloatMembers {
    input := {
        @required
        requiredFloat: Float

        optionalFloat: Float

        @default(1.0)
        defaultFloat: Float
    }
}
