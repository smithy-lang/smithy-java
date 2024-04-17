$version: "2"

namespace smithy.java.codegen.test.structures.members

operation Timestamps {
    input := {
        @required
        requiredTimestamp: Timestamp

        optionalTimestamp: Timestamp

        @default("1985-04-12T23:20:50.52Z")
        defaultTimestamp: Timestamp
    }
}
