$version: "2.0"

namespace smithy.java.codegen.server.test

use aws.protocols#restJson1
use smithy.test.auth#testAuthScheme

@paginated(
    inputToken: "inputToken",
    pageSize: "maxItems"
)
@restJson1
@testAuthScheme
service TestService {
    version: "today"
    operations: [
        Echo
        PaginatedOperation
    ]
}

@http(method: "PUT", uri: "/echo")
operation Echo {
    input := {
        string: String
    }
    output := {
        string: String
    }
}

/// Tests the generation and compilation of pagination method.
/// NOTE: No actual implementation is provided in test server.
@http(method: "GET", uri: "/list", code: 200)
@paginated(
    inputToken: "inputToken",
    outputToken: "outputToken",
    items: "results",
    pageSize: "maxItems"
)
operation PaginatedOperation {
    input := {
        @httpQuery("maxItems")
        maxItems: Integer
        @httpQuery("input")
        inputToken: String
    }
    output := {
        results: ResultsList
        outputToken: String
    }
}

@private
list ResultsList {
    member: String
}
