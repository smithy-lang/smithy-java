$version: "2"

namespace smithy.java.codegen.types.naming

structure NamingStruct {
    // Collides with `other` in equals
    other: String

    builder: Builder

    type: Type

    object: Object

    union: UnionWithTypeMember

    map: Map

    list: List
}

@private
structure Builder {}

@private
structure Type {}

// All of the members of this structure would override
// Object.class method's unless escaped.
@private
structure Object {
    class: String
    getClass: String
    hashCode: String
    clone: String
    toString: String
    notify: String
    notifyAll: String
    wait: String
    finalize: String
}

@private
union UnionWithTypeMember {
    type: Type
}

@private
structure Map {}

@private
structure List {}
