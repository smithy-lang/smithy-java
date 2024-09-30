plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides a test harness and tools for executing protocol tests."

extra["displayName"] = "Smithy :: Java :: Protocol Tests"
extra["moduleName"] = "software.amazon.smithy.java.protocol-tests"

dependencies {
    implementation(project(":logging"))
    implementation(project(":codegen:client"))
    implementation(libs.smithy.codegen)
    implementation(project(":client-core"))
    implementation(libs.smithy.protocol.test.traits)
    implementation(project(":http-api"))
    implementation(project(":codegen:core"))
    implementation(project(":codegen:server"))
    implementation(project(":server"))
    implementation(project(":server-core"))
    implementation(project(":client-http"))

    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.engine)
    api(libs.junit.jupiter.params)
    api(libs.hamcrest)
}
