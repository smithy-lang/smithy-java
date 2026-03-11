plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.publishing-conventions")
}

description = "This module provides the core client API types"

extra["displayName"] = "Smithy :: Java :: Client :: API"
extra["moduleName"] = "software.amazon.smithy.java.client.core"

dependencies {
    api(project(":context"))
    api(project(":core"))
    api(project(":auth-api"))
    api(project(":client:client-auth-api"))
    api(project(":retries-api"))
    implementation(project(":logging"))
}
