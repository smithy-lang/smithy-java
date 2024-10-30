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

        object: Object
    }
}

@private
structure Builder {}

@private
structure InnerDeserializer {}

@private
structure Type {}

// All of the members of this structure would override
// Object.class method's unless escaped.
@private
structure Object {
    getClass: String
    hashCode: String
    clone: String
    toString: String
    notify: String
    notifyAll: String
    wait: String
    finalize: String
}
