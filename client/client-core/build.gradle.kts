plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the core client functionality"

extra["displayName"] = "Smithy :: Java :: Client Core"
extra["moduleName"] = "software.amazon.smithy.java.client.core"

dependencies {
    api(project(":context"))
    api(project(":core"))
    api(project(":auth-api"))
    api(project(":retries-api"))
    api(project(":framework-errors"))
    implementation(project(":logging"))

    testImplementation(project(":client:dynamic-client"))
    testImplementation(project(":aws:client:aws-client-restjson"))
    testImplementation(project(":client:client-mock-plugin"))
}
