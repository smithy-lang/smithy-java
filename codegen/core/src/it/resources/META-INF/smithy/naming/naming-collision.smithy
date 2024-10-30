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
        
        // Collides with `serializer` input to serializeMembers
        serializer: String
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

/// This will clash with built in `java.lang` exception used a number
/// of places such as in enums and unions
@private
@error("client")
structure IllegalArgumentException {}
