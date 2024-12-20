rootProject.name = "smithy-java"

include("logging")
include(":context")
include(":io")
include(":core")

// Codegen Base
include(":codegen:core")
include(":codegen:plugins")

// Codegen Plugins
include(":codegen:plugins:client")
include(":codegen:plugins:server")
include(":codegen:plugins:types")

// Framework errors
include(":framework-errors")

// Protocol tests
include(":protocol-tests")
include(":mock-client-plugin")

include("tracing-api")

include(":http-api")
include(":http-binding")

include(":json-codec")
include(":xml-codec")
include(":rpcv2-cbor-codec")

include(":client-core")
include(":client-http")
include(":client-http-binding")

include(":dynamic-client")

include(":auth-api")
include(":retries-api")

// server
include("server-core")
include("server-api")
include("server-netty")
include("server-core")
include("server-aws-rest-json1")

// Examples
include(":examples:restjson-example")
include(":examples:dynamodb")
include(":examples:server-example")
include(":examples:event-streaming")
include(":examples:end-to-end-example")
include(":examples:lambda-endpoint")
include(":examples:standalone-types-example")
include("examples:shared-types-example")
include(":examples:middleware-example:middleware-model")
include(":examples:middleware-example:client-integration")
include(":examples:middleware-example:server-integration")

// AWS specific
include(":aws:event-streams")
include(":aws:aws-client-core")
include(":aws:sigv4")
include(":aws:client-awsjson")
include(":aws:client-restjson")
include(":aws:client-restxml")
include(":aws:client-rpcv2-cbor-protocol")
include(":aws:aws-client-http")
include(":aws:sdkv2-retries")

// AWS integrations
include(":aws:integrations:lambda")

include(":server-rpcv2-cbor")
include(":aws:sdkv2-shapes")
include(":aws:sdkv2-shapes")
