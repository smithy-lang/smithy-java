$version: "2"

namespace smithy.java.codegen.tostring

structure Plain {
    name: String
}

union Choice {
    text: String
    count: Integer
}
