$version: "2"

namespace smithy.java.codegen.test.recursion

operation RecursiveMaps {
    input := {
        recursiveMap: RecursiveMap
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
