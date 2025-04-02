$version: "2"

namespace smithy.java.codegen.test.enums

/// Test only checks for successful compilation
operation UnsafeValueName {
    input := {
        enum: UnsafeValueEnum
    }
}

@private
enum UnsafeValueEnum {
    A = "./U/Y/Q/.../?"
    B = "/////////////"
}
