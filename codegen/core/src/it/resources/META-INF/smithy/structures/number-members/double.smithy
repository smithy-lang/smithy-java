$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Doubles {
    input := {
        @required
        requiredDouble: Double

        optionalDouble: Double

        @default(1.0)
        defaultDouble: Double
    }
}
