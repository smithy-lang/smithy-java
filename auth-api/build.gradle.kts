plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the auth API"

extra["displayName"] = "Smithy :: Java :: Auth API"
extra["moduleName"] = "software.amazon.smithy.java.auth-api"

dependencies {
    api(project(":context"))
    // TODO: Can we avoid this in this package?
    api(libs.smithy.model)
}
