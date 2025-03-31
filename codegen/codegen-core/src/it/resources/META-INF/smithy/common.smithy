$version: "2"

namespace smithy.java.codegen.test.common

structure NestedStruct {
    fieldA: String
    fieldB: Integer
}

intEnum NestedIntEnum {
    A = 1
    B = 2
}

enum NestedEnum {
    A
    B
}

union NestedUnion {
    a: String
    b: Integer
}

list ListOfString {
    member: String
}

map StringStringMap {
    key: String
    value: String
}

@streaming
blob StreamingBlob

@uniqueItems
list SetOfStrings {
    member: String
}
