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
include(":protocol-tests")

include("tracing-api")

include(":http-api")
include(":http-binding")
include(":http-auth")


include(":json-codec")

include(":client-core")
include(":client-endpoint-api")
include(":client-http")
include(":client-auth-api")

include(":auth-api")
include(":sigv4")

// server
include("server-core")
include("server")
include("server-netty")

// Examples
include(":examples:restjson-example")
include(":examples:dynamodb")

// Server
include("server-core")

// AWS specific
include(":aws:event-streams")
include(":aws:client-http")
include(":aws:client-json-protocols")
