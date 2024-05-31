$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Enums {
    input := {
        @required
        requiredEnum: EnumType
        optionalEnum: EnumType
    }
}

@private
enum EnumType {
    OPTION_ONE = "option-one"
    OPTION_TWO = "option-two"
}
