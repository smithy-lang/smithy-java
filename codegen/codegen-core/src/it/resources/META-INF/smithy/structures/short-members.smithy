$version: "2"

namespace smithy.java.codegen.test.structures

operation ShortMembers {
    input := {
        @required
        requiredShort: Short

        optionalShort: Short

        @default(1)
        defaultShort: Short
    }
}
