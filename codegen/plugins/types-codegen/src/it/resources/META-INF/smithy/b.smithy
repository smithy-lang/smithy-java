$version: "2.0"

namespace smithy.java.codegen.types.test

enum YesOrNo {
    YES
    NO
}

structure MyStruct {
    fieldA: String
    fieldB: MyNestedStruct
}

@private
structure MyNestedStruct {
    fieldC: Integer
    fieldD: Float
}
