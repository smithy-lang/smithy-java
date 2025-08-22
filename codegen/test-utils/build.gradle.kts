
plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides utilities for testing codegen plugins"

extra["displayName"] = "Smithy :: Java :: Codegen :: Test"
extra["moduleName"] = "software.amazon.smithy.java.codegen.test"

dependencies {
    implementation(libs.smithy.codegen)
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
}
