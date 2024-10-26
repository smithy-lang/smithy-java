rootProject.name = "smithy-java"

include("logging")
include(":context")
include(":io")
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

include(":json-codec")
include(":xml-codec")

include(":client-core")
include(":client-http")
include(":client-http-binding")

include(":dynamic-client")

include(":auth-api")
include(":retries-api")
include(":retries-sdk-adapter")

// server
include("server-core")
include("server")
include("server-netty")
include("server-core")
include("server-aws-rest-json1")

// Examples
include(":examples:restjson-example")
include(":examples:dynamodb")
include(":examples:server-example")
include(":examples:event-streaming")
include(":examples:end-to-end-example")

// AWS specific
include(":aws:event-streams")
include(":aws:aws-client-core")
include(":aws:sigv4")
include(":aws:client-awsjson")
include(":aws:client-restjson")
include(":aws:client-rest-xml")
