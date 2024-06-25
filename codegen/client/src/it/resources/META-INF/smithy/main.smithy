$version: "2.0"

namespace smithy.java.codegen.server.test

use aws.protocols#restJson1

@restJson1
service TestService {
    version: "today"
    operations: [
        Echo
    ]
}

@http(method: "PUT", uri: "/anything")
operation Echo {
    input := {
        string: String
    }
    output := {
        string: String
    }
}
