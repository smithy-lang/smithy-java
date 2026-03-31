$version: "2"

namespace smithy.java.codegen

service TestService {
    version: "today"
    operations: [
        JSpecifyAnnotationStruct
        AdditionalJSpecifyTests
    ]
}

operation JSpecifyAnnotationStruct {
    input := {
        @required
        requiredStruct: RequiredStruct

        @required
        requiredPrimitive: Boolean

        optionalString: String

        optionalPrimitive: Integer
    }
}

@private
structure RequiredStruct {
    @required
    member: String
}

operation AdditionalJSpecifyTests {
    input := {
        union: TestUnion
        enum: TestEnum
        sparseList: SparseStringList
        sparseMap: SparseStringMap
    }
}

@sparse
list SparseStringList {
    member: String
}

@sparse
map SparseStringMap {
    key: String
    value: String
}

@private
union TestUnion {
    boxedVariant: String
    primitiveVariant: String
}

@private
enum TestEnum {
    A
    B
}
