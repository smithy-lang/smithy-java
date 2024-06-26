$version: "2"

namespace smithy.java.codegen.integrations.javadoc

service TestService {
    version: "today"
    operations: [
        SpecialCased
    ]
    errors: [
        RetryableError
    ]
}

operation SpecialCased {
    input := {
        stringDefault: String = "string"
        @length(min: 10)
        stringWithLength: String
        @range(max: 100)
        numberWithRange: Integer
        @xmlNamespace(uri: "http://foo.com")
        xmlNamespaced: String
    }
}

@error("client")
@httpError(403)
@retryable
structure RetryableError {
    @required
    message: String
}
