$version: "2"

namespace smithy.java.codegen.test.recursion

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
