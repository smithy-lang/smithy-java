plugins {
    id("smithy-java.module-conventions")
}

description =
    "MCP Server support"

extra["displayName"] = "Smithy :: Java :: MCP Server"
extra["moduleName"] = "software.amazon.smithy.java.server.mcp"

dependencies {
    api(project(":server:server-api"))
    implementation(project(":server:server-core"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(project(":codecs:json-codec"))
    implementation(project(":mcp:mcp-schemas"))
    implementation(project(":model-bundler:bundle-api"))
}

spotbugs {
    ignoreFailures = true
}
