$version: "2"

namespace smithy.java.codegen

service TestService {
    version: "today"
    operations: [
        NonNullAnnotation
    ]
}

operation NonNullAnnotation {
    input := {
        @required
        requiredStruct: RequiredStruct
    }
}

@private
structure RequiredStruct {
    @required
    member: String
}
