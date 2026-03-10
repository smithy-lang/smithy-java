$version: "2.0"

namespace smithy.java.codegen.test.idempotencytoken

operation IdempotencyTokenRequired {
    input := {
        @idempotencyToken
        @required
        token: String
    }
}
