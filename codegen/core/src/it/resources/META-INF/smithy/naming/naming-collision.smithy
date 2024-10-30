$version: "2"

namespace smithy.java.codegen.test.naming

/// Compile-only checks that naming collisions are handled correctly and
/// generate valid code.
operation Naming {
    input := {
        // Collides with `other` in equals
        other: String

        builder: Builder

        inner: InnerDeserializer

        type: Type

        // Collides with `serializer` input to serializeMembers
        serializer: String
    }
}

@private
structure Builder {}

@private
structure InnerDeserializer {}

@private
structure Type {}
