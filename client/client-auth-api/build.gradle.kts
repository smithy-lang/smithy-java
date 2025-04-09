plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the client auth APIs"

extra["displayName"] = "Smithy :: Java :: Client :: Auth API"
extra["moduleName"] = "software.amazon.smithy.java.client.core.auth"

dependencies {
    api(project(":auth-api"))
    api(project(":core"))
    implementation(libs.smithy.model)
}
