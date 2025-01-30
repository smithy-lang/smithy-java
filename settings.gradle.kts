rootProject.name = "smithy-java"

// Common modules
include("logging")
include(":context")
include(":io")
include(":core")

// Common components
include(":auth-api")
include(":retries-api")
include(":tracing-api")
include("http-api")
include("http-binding")
include(":framework-errors")

// Codecs
include(":codecs:json-codec")
include(":codecs:cbor-codec")
include(":codecs:xml-codec")

// Client
include(":client:client-core")
include(":client:client-http")
include(":client:client-http-binding")
include(":client:client-rpcv2-cbor")
include(":client:dynamic-client")
include(":client:mock-client-plugin")

// Server
include(":server:server-api")
include(":server:server-core")
include(":server:server-netty")
include(":server:server-rpcv2-cbor")

// Codegen
include(":codegen:core")
include(":codegen:plugins")
include(":codegen:plugins:client")
include(":codegen:plugins:server")
include(":codegen:plugins:types")

// Utilities
include(":protocol-test-harness")
include(":jmespath")

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
include(":aws:sigv4")
include(":aws:client:aws-client-core")
include(":aws:client:aws-client-awsjson")
include(":aws:client:aws-client-restjson")
include(":aws:client:aws-client-restxml")
include(":aws:client:aws-client-http")
include(":aws:client:sdkv2-retries")
include(":aws:client:sdkv2-shapes")
include(":aws:server:aws-server-restjson")
include(":aws:integrations:lambda")

// Compatibility
include(":aws:sdkv2-shapes")
include(":aws:sdkv2-shapes")
