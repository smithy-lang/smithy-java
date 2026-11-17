$version: "2"

namespace smithy.test

union OuterUnion {
    inner: InnerUnion
    other: String
}

union InnerUnion {
    deepest: DeepestUnion
    number: Integer
}

/// Points back to OuterUnion, making the chain of unions recursive.
union DeepestUnion {
    outer: OuterUnion
    text: String
}
