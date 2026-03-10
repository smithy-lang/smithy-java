$version: "2"

namespace smithy.java.codegen.test.structures

operation ByteMembers {
    input := {
        @required
        requiredByte: Byte

        optionalByte: Byte

        @default(1)
        defaultByte: Byte
    }
}
