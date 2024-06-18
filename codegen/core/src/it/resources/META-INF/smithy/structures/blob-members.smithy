$version: "2"

namespace smithy.java.codegen.test.structures

use smithy.java.codegen.test.common#StreamingBlob

operation BlobMembers {
    input := {
        @required
        requiredBlob: Blob

        optionalBlob: Blob

        // A streaming blob member. Streaming blobs must be marked as required
        @required
        streamingBlob: StreamingBlob
    }
}
