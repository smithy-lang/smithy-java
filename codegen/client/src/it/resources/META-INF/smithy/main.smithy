$version: "2.0"

namespace smithy.java.codegen.server.test

use aws.protocols#restJson1

@restJson1
@httpBasicAuth
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
