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

    listOfList: ListOfList

    mapOfMap: MapOfMap
}

list ListOfList{
    member:List
}

map MapOfMap {
    key: String
    value: Map
}

structure Builder {}

structure Type {}

// All of the members of this structure would override
// Object.class method's unless escaped.
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
