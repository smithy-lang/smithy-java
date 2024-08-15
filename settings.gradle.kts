rootProject.name = "smithy-java"

include("logging")
include(":context")
include(":core")

// Codegen
include(":codegen:core")
include(":codegen:client")
include(":codegen:server")
include(":codegen:types")

// Protocol tests
include(":protocol-tests:client")

include("tracing-api")

include(":http-api")
include(":http-binding")
include(":aws-event-streams")

include(":json-codec")

include(":client-core")
include(":client-endpoint-api")
include(":client-http")
include(":client-auth")

include(":auth-api")
include(":sigv4")

// Protocols
include(":client-aws-rest-json1")

// Examples
include(":examples:restjson-example")

// Server
include("server-core")
