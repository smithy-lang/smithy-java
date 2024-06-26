$version: "2"

namespace smithy.java.codegen.test.traits

/// Tests that traits are correctly added to schemas and initialize properly
/// when custom initializers are used.
operation Traits {
    input := {
        stringDefault: String = "string"
        @length(min: 10)
        stringWithLength: String
        @range(max: 100)
        numberWithRange: Integer
        @xmlNamespace(uri: "http://foo.com")
        xmlNamespaced: String
    }

    errors: [
        RetryableError
    ]
}

@error("client")
@httpError(403)
@retryable
structure RetryableError {
    @required
    message: String
}
