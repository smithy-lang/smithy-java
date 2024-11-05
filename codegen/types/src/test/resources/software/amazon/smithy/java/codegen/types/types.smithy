$version: "2"

namespace smithy.java.codegen.types.test

structure StructureShape {
    fieldA: String
    fieldB: String
}

union UnionShape {
    variantA: String
    variantB: Integer
}

enum EnumShape {
    A
    B
}

intEnum IntEnumShape {
    ONE = 1
    TWO = 2
}
