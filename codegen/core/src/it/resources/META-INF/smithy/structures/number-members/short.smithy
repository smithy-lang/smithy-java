$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Shorts {
    input := {
        @required
        requiredShort: Short

        optionalShort: Short

        @default(1)
        defaultShort: Short
    }
}
