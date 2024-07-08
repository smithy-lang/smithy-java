$version: "2"

namespace smithy.java.codegen.test.recursion

operation RecursiveStructs {
    input := {
        recursiveStructs: RecursiveStructA
    }
}

@private
structure RecursiveStructA {
    b: RecursiveStructB
}

@private
structure RecursiveStructB {
    a: RecursiveStructA
}
