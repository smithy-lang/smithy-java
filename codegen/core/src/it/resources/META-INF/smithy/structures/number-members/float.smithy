$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Floats {
    input := {
        @required
        requiredFloat: Float

        optionalFloat: Float

        @default(1.0)
        defaultFloat: Float
    }
}
