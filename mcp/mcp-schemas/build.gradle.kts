plugins {
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.java.codegen")
    alias(libs.plugins.smithy.gradle.jar)
}

description = "This module provides a schemas for MCP integration"

extra["displayName"] = "Smithy :: Java :: MCP Schemas"
extra["moduleName"] = "software.amazon.smithy.mcp.schemas"

smithyJava {
    autoAddDependencies = false
    additionalSmithyBuildPlugins.add("trait-codegen")
}

dependencies {
    api(project(":core"))
    api(libs.smithy.model)
    api(project(":server:server-api"))
    api(project(":framework-errors"))
    api(project(":smithy-ai-traits"))
    smithyBuild(project(":codegen:codegen-plugin"))
    smithyBuild(project(":server:server-api"))
    smithyBuild(libs.smithy.traitcodegen)
}

tasks.sourcesJar {
    dependsOn("smithyJarStaging")
}

tasks.jar {
    doFirst {
        manifest.attributes.remove("Build-Timestamp")
        manifest.attributes.remove("Build-OS")
        manifest.attributes.remove("Build-Jdk")
    }
}
