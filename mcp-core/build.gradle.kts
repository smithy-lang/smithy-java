plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the MCP core classes"

extra["displayName"] = "Smithy :: Java :: MCP CORE"
extra["moduleName"] = "software.amazon.smithy.java.mcp.core"

dependencies {
    implementation(project(":core"))
    implementation(project(":server-api"))
}
