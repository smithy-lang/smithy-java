$version: "2"

namespace smithy.java.codegen.test.structures

operation IntEnumMembers {
    input := {
        @required
        requiredEnum: IntEnumType

        @default(1)
        defaultEnum: IntEnumType

        optionalEnum: IntEnumType
    }
}

@private
intEnum IntEnumType {
    OPTION_ONE = 1
    OPTION_TWO = 10
}
