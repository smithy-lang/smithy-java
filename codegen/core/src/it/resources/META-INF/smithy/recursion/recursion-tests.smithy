$version: "2"

namespace smithy.java.codegen.test.recursion

resource RecursionTests {
    operations: [
        RecursiveLists
        RecursiveMaps
        RecursiveStructs
        SelfReference
        MultipleRecursion
    ]
}
