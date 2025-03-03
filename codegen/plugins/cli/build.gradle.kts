plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy Java cli codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Plugins :: CLI"
extra["moduleName"] = "software.amazon.smithy.java.codegen.cli"

dependencies {
    implementation(project(":cli:cli-core"))
    // For http transport
    implementation(project(":client:client-http"))
}

// TODO: Add tests for generated code
