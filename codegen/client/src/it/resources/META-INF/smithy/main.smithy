$version: "2.0"

namespace smithy.java.codegen.server.test

use aws.protocols#restJson1
use smithy.test.auth#testAuthScheme

@restJson1
@testAuthScheme
service TestService {
    version: "today"
    operations: [
        Echo
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
