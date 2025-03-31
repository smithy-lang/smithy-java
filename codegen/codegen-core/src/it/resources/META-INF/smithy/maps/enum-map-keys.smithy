$version: "2"

namespace smithy.java.codegen.test.maps

operation EnumMapKeys {
    input := {
        mapOfEnumValue: EnumKeyMap
    }
}

map EnumKeyMap {
    key: EnumKey
    value: String
}

enum EnumKey {
    A
    B
    C
    D
}
