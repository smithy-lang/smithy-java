$version: "2.0"

namespace smithy.java.codegen.types.test

use smithy.java.codegen.types.nested.test#NestedIntEnum

structure UsesOtherStructs {
    nested: NestedIntEnum
    other: MyStruct
}

union MyUnion {
    optionA: String
    optionB: Integer
}

structure Astruct {
    value: String
    b: Bstruct
}

structure Bstruct {
    a: Astruct
}
