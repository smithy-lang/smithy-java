$version: "2"

namespace smithy.java.codegen.test.enums

/// Test only checks for successful compilation
operation NonSequentialIntEnum {
    input := {
        enum: NonSequential
    }
}

intEnum NonSequential {
    ONE = 1
    TEN = 10
    TWO = 2
    TWENTY = 20
}
