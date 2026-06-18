$version: "2.0"

metadata shapeClosures = [
    {
        id: "smithy.java.codegen.types.test#authoredClosure"
        includeBySelector: "[id = 'smithy.java.codegen.types.test#StructureShape']"
        rename: {
            "smithy.java.codegen.types.test#StructureShape": "AuthoredStructure"
        }
    }
]

namespace smithy.java.codegen.types.test

structure StructureShape {
    fieldA: String
    fieldB: String
}

union UnionShape {
    a: String
    b: Integer
}

enum EnumShape {
    A
    B
}

intEnum IntEnumShape {
    A = 1
    B = 2
}
