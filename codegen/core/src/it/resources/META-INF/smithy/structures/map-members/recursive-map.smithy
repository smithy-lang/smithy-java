$version: "2"

namespace smithy.java.codegen.test.structures.members

operation RecursiveMaps {
    input := {
        recursiveList: RecursiveList
    }
}

@private
map RecursiveMap {
    key: String
    value: IntermediateMapStructure
}

@private
structure IntermediateMapStructure {
    foo: RecursiveMap
}
