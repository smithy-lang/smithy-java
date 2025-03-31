$version: "2"

namespace smithy.java.codegen.test.structures

use smithy.java.codegen.test.common#StreamingBlob

/// This operation tests only compilation.
operation StreamingBlobMembers {
    input := {
        // A streaming blob member. Streaming blobs must be marked as required
        @required
        streamingBlob: StreamingBlob
    }
}
