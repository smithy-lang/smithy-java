$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Blobs {
    input := {
        @required
        requiredBlob: Blob

        optionalBlob: Blob

        // A streaming blob member. Streaming blobs must be marked as required
        @required
        streamingBlob: StreamingBlob
    }
}

@private
@streaming
blob StreamingBlob
