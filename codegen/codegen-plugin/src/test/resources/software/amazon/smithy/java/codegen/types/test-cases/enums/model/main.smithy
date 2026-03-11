$version: "2"

namespace smithy.java.codegen.types.naming

structure EnumStructure {
    enumType : EnumType
    intEnumType: IntEnumType
}

enum EnumType {
    OPTION_ONE = "option-one"
    OPTION_TWO = "option-two"
}

enum TextType {
    TEXT
}

intEnum IntEnumType {
    FIRST = 1
    SECOND = 2
    FIFTH = 5
}
