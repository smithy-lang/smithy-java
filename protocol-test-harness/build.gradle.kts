

plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides a test harness and tools for executing protocol tests."

extra["displayName"] = "Smithy :: Java :: Protocol Tests"
extra["moduleName"] = "software.amazon.smithy.java.protocoltests"

dependencies {
    implementation(project(":logging"))
    implementation(project(":codegen:plugins"))
    implementation(project(":codegen:codegen-core"))
    implementation(libs.smithy.codegen)
    implementation(project(":client:client-core"))
    implementation(libs.smithy.protocol.test.traits)
    implementation(project(":http:http-api"))
    implementation(project(":server:server-api"))
    implementation(project(":server:server-core"))
    implementation(project(":client:client-http"))
    implementation(project(":codecs:json-codec"))
    implementation(libs.assertj.core)

    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.engine)
    api(libs.junit.jupiter.params)
}
