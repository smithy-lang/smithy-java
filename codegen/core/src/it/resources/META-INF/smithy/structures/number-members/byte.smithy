$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Bytes {
    input := {
        @required
        requiredByte: Byte

        optionalByte: Byte

        @default(1)
        defaultByte: Byte
    }
}
