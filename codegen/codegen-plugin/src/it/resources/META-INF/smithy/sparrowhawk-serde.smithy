$version: "2.0"

namespace smithy.java.codegen.types.sparrowhawk

use smithy.protocols#idx

/// Mirrors the reference smithy-sparrowhawk-java demo.smithy model so generated code can be checked for
/// byte-compatibility against reference-produced golden payloads.
structure SparrowhawkDemoInput {
    @idx(1)
    @required
    str: String

    @idx(2)
    @required
    f: Float = 0

    @idx(3)
    @required
    d: Double = 0

    @idx(4)
    @required
    i: Integer = 0

    @idx(5)
    @required
    bytes: Blob

    @idx(6)
    nested: SparrowhawkNestedStructure
}

structure SparrowhawkNestedStructure {
    @idx(1)
    @required
    innerStr: String

    @idx(2)
    @required
    list: SparrowhawkVarintList
}

list SparrowhawkVarintList {
    member: Integer
}
