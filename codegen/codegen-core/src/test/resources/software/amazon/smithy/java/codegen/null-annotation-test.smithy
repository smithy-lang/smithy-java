$version: "2"

namespace smithy.java.codegen

service TestService {
    version: "today"
    operations: [
        NonNullAnnotationStruct
        AdditionalNullAnnotationTests
    ]
}

operation NonNullAnnotationStruct {
    input := {
        @required
        requiredStruct: RequiredStruct

        @required
        requiredPrimitive: Boolean
    }
}

@private
structure RequiredStruct {
    @required
    member: String
}

operation AdditionalNullAnnotationTests {
    input := {
        union: TestUnion
        enum: TestEnum
    }
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
