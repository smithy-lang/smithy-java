plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the public Server API"

extra["displayName"] = "Smithy :: Java :: Server :: API"
extra["moduleName"] = "software.amazon.smithy.java.server.api"

dependencies {
    implementation(project(":logging"))
    implementation(project(":core"))
    implementation(project(":framework-errors"))
}
