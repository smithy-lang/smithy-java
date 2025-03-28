$version: "2"

namespace smithy.java.codegen.test.maps

operation NestedMaps {
    input := {
        mapOfStringMap: MapOfStringMap
        mapOfMapOfStringMap: MapOfMapOfStringMap
        mapOfStringList: MapOfStringList
        mapOfMapList: MapOfMapList
    }
}

@private
map MapOfMapOfStringMap {
    key: String
    value: MapOfStringMap
}

@private
map MapOfStringMap {
    key: String
    value: StringMap
}

@private
map StringMap {
    key: String
    value: String
}

@private
map MapOfStringList {
    key: String
    value: StringList
}

@private
list StringList {
    member: String
}

@private
map MapOfMapList {
    key: String
    value: MapList
}

@private
list MapList {
    member: StringMap
}
