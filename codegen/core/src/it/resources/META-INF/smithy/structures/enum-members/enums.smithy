$version: "2"

namespace smithy.java.codegen.test.structures.members

operation IntEnums {
    input := {
        @required
        requiredEnum: IntEnumType
        optionalEnum: IntEnumType
    }
}

@private
intEnum IntEnumType {
    OPTION_ONE = 1
    OPTION_TWO = 10
}
