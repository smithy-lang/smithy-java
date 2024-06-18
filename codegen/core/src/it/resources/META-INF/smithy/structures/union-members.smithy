$version: "2"

namespace smithy.java.codegen.test.structures

use smithy.java.codegen.test.common#NestedUnion

operation UnionMembers {
    input := {
        @required
        requiredUnion: NestedUnion

        optionalUnion: NestedUnion
    }
}
