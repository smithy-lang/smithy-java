$version: "2"

namespace smithy.java.codegen.test.structures

operation DoubleMembers {
    input := {
        @required
        requiredDouble: Double

        optionalDouble: Double

        @default(1.0)
        defaultDouble: Double
    }
}
