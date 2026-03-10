$version: "2"

namespace smithy.java.codegen

service TestService {
    operations: [
        TestOperation
    ]
}

operation TestOperation {
    input := {
        foo: String
        stringList: StringList
        stringEnum: StringEnum
        intEnum: IntEnum
        stringMap: StringMap
    }
}

list StringList {
    member: String
}

enum StringEnum {
    FOO
}

intEnum IntEnum {
    BAR = 1
}

map StringMap {
    key: String
    value: String
}

map EnumKeyMap {
    key: StringEnum
    value: String
}

union BasicUnion {
    foo: String
}
