$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Documents {
    input := {
        @required
        requiredDoc: Document

        optionalDocument: Document
    }
}
