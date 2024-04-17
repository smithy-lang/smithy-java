$version: "2"

namespace smithy.java.codegen.test.structures.members

operation RecursiveLists {
    input := {
        recursiveList: RecursiveList
    }
}

@private
list RecursiveList {
    member: IntermediateListStructure
}

@private
structure IntermediateListStructure {
    foo: RecursiveList
}
