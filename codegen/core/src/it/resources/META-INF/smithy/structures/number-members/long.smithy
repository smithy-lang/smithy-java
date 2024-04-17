$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Longs {
    input := {
        @required
        requiredLongs: Long

        optionalLongs: Long

        @default(1)
        defaultShort: Long
    }
}
