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
    }
    errors: [
        IllegalArgumentException
    ]
}

@private
structure Builder {}

@private
structure InnerDeserializer {}

@private
structure Type {}

/// This will clash with built in `java.lang` exception used a number
/// of places such as in enums and unions
@private
@error("client")
structure IllegalArgumentException {}
