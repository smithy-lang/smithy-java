//This ensures composite builds also have the repositories configured.
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }

    // The io.quarkus.extension Gradle plugin is used by the experimental
    // quarkus-smithy module to generate Quarkus extension metadata. Pinned
    // here so the version is consistent with the Quarkus BOM that module uses.
    plugins {
        id("io.quarkus.extension") version "3.35.3"
    }
}

rootProject.name = "smithy-java"

// BOM (Bill of Materials)
include(":bom")

// AI
include(":smithy-ai-traits")

// Common modules
include(":context")
include(":core")
include(":dynamic-schemas")
include(":io")
include(":logging")
include(":version-spi")

// CLI
include(":cli")

// Common components
include(":auth-api")
include(":endpoints")
include(":framework-errors")
include(":http:http-api")
include(":http:http-binding")
include(":http:http-client")
include(":retries-api")
include(":retries")

// Codecs
include(":codecs:cbor-codec")
include(":codecs:json-codec")
include(":codecs:xml-codec")

// Client
include(":client:client-core")
include(":client:client-auth-api")
include(":client:client-http")
include(":client:client-http-binding")
include(":client:client-rpcv2")
include(":client:client-rpcv2-cbor")
include(":client:client-rpcv2-json")
include(":client:dynamic-client")
include(":client:client-mock-plugin")
include(":client:client-waiters")
include(":client:client-rulesengine")
include(":client:client-metrics-otel")

// Server
include(":server:server-api")
include(":server:server-core")
include(":server:server-netty")
include(":server:server-vertx")
include(":server:server-rpcv2")
include(":server:server-rpcv2-cbor")
include(":server:server-rpcv2-json")
include(":server:server-proxy")

// Codegen
include(":codegen:codegen-core")
include(":codegen:codegen-plugin")

// Experimental: Quarkus extension (mirrors the structure of quarkus-grpc-zero).
// Flat layout so the published artifact IDs match each project's Gradle name
// and the root build's dependency-substitution rule works for the example.
// Naming follows the canonical Quarkus convention: runtime artifact has no
// suffix (`quarkus-smithy`), deployment artifact has `-deployment` suffix.
include(":quarkus-smithy")
include(":quarkus-smithy-deployment")
include(":quarkus-smithy-integration-tests")

// Utilities
include(":jmespath")
include(":rulesengine")
include(":protocol-test-harness")
include(":fuzz-test-harness")

// AWS specific
include(":aws:aws-event-streams")
include(":aws:aws-sigv4")
include(":aws:client:aws-client-awsjson")
include(":aws:client:aws-client-core")
include(":aws:client:aws-client-http")
include(":aws:client:aws-client-restjson")
include(":aws:client:aws-client-restxml")
include(":aws:client:aws-client-awsquery")
include(":aws:client:aws-client-rulesengine")
include(":aws:integrations:aws-lambda-endpoint")
include(":aws:server:aws-server-restjson")
include(":aws:aws-auth-api")

// AWS service bundling code
include(":aws:aws-service-bundle")
include(":aws:aws-service-bundler")
include(":aws:aws-mcp-types")

// AWS SDK V2 shims
include(":aws:sdkv2:aws-sdkv2-retries")
include(":aws:sdkv2:aws-sdkv2-shapes")
include(":aws:sdkv2:aws-sdkv2-auth")

// Examples
include(":examples")
include(":examples:basic-server")
include(":examples:transcribestreaming-client")
include(":examples:end-to-end")
include(":examples:event-streaming-client")
include(":examples:lambda")
include(":examples:restjson-client")
include(":examples:standalone-types")
include(":examples:mcp-server")
include(":examples:mcp-traits-example")
// :examples:quarkus-server is intentionally NOT included here. It is a
// standalone Gradle build that consumes smithy-java only via mavenLocal,
// matching how real customers will use the quarkus-smithy extension.
// Including it as a subproject causes Quarkus dev mode to substitute
// sibling smithy-java projects' raw `build/classes` for their published
// jars, which (a) bypasses json-codec's shadowJar that relocates Jackson 3,
// and (b) splits classloaders so SchemaExtensionKey ids drift between the
// non-reloadable and reloadable classloader buckets, breaking JSON serde.
// Run with: `cd examples/quarkus-server && gradle quarkusDev`.

//MCP
include(":mcp")
include(":mcp:mcp-schemas")
include(":mcp:mcp-server")

include(":model-bundle")
include(":model-bundle:model-bundle-api")

// Benchmarks (not published)
include(":benchmarks")
include(":benchmarks:serde-benchmarks")
