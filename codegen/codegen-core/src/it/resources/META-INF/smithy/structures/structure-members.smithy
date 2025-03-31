$version: "2"

namespace smithy.java.codegen.test.structures

use smithy.java.codegen.test.common#NestedStruct

operation StructureMembers {
    input := {
        @required
        requiredStruct: NestedStruct

        optionalStruct: NestedStruct
    }
}
