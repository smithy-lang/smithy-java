

plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.integ-test-conventions")
}

description = "This module provides a test harness and tools for executing protocol tests."

dependencies {
    implementation(project(":logging"))
    implementation(project(":codegen:codegen-plugin"))
    implementation(libs.smithy.codegen)
    implementation(project(":client:client-core"))
    implementation(libs.smithy.protocol.test.traits)
    implementation(project(":http:http-api"))
    implementation(project(":server:server-api"))
    implementation(project(":server:server-core"))
    implementation(project(":client:client-http"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(libs.assertj.core)

    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.engine)
    api(libs.junit.jupiter.params)
}
