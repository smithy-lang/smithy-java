$version: "2"

namespace smithy.java.codegen.test.structures

operation BlobMembers {
    input := {
        @required
        requiredBlob: Blob

        optionalBlob: Blob
    }
}
