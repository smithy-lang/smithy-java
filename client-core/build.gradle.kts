plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the core client functionality"

extra["displayName"] = "Smithy :: Java :: Client Core"
extra["moduleName"] = "software.amazon.smithy.java.client-core"

dependencies {
    api(project(":context"))
    api(project(":core"))
    api(project(":auth-api"))
    api(project(":retries-api"))
    api(project(":framework-errors"))
    // TODO: Remove once this is in smithy-model
    api(project(":smithy-implicit-error-trait"))
    implementation(project(":logging"))

    testImplementation(project(":dynamic-client"))
    testImplementation(project(":aws:client-restjson"))
    testImplementation(project(":mock-client-plugin"))
}
