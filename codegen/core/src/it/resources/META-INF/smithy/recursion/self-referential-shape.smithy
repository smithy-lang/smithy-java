$version: "2"

namespace smithy.java.codegen.test.recursion

operation SelfReference {
    input := {
        selfReferentialShape: SelfReferencing
    }
}

@private
structure SelfReferencing {
    self: SelfReferencing
}
