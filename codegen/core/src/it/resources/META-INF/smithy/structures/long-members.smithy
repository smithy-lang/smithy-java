$version: "2"

namespace smithy.java.codegen.test.structures

operation LongMembers {
    input := {
        @required
        requiredLongs: Long

        optionalLongs: Long

        @default(1)
        defaultShort: Long
    }
}
