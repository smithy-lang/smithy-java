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

union UnionWithTypeMember {
    type: Type
}

structure Map {}

structure List {}
