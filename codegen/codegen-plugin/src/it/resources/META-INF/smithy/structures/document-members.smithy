$version: "2"

namespace smithy.java.codegen.test.structures

operation DocumentMembers {
    input := {
        @required
        requiredDoc: Document

        optionalDocument: Document
    }
}
