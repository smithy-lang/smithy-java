$version: "2.0"

namespace smithy.java.codegen.server.test

use aws.protocols#restJson1

@authDefinition
@trait(selector: "service")
structure exampleAuth {}

@restJson1
@exampleAuth
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
