pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.0"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "smithy-java"

include(":core")

// Codegen
include(":codegen:core")
include(":codegen:client")
include(":codegen:server")
include(":codegen:types")

include("tracing-api")

include(":http-api")
include(":http-binding")

include(":json-codec")

include(":client-core")
include(":client-endpoint-api")
include(":client-http")
include(":client-auth")

include(":identity-api")
include(":auth-api")
include(":sigv4")

// Protocols
include(":client-aws-rest-json1")

// Examples
include(":examples:restjson-example")
include("server-core")
include("server-netty")
include("server")
include("server-netty")
include("server-protocols")
include("server-protocols:restJson")
include("server-protocol-tests")
include("kestrel-codec")
include("codegen:kestrel")
include("internal-traits")
include("codegen:kestrel-smithy-interop")
include("kestrel")
include("server-protocols:rpcV2Kestrel")
include(":vend-server")
include("vend-servergen")
