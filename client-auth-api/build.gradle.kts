plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides client auth API"

extra["displayName"] = "Smithy :: Java :: Client Auth API"
extra["moduleName"] = "software.amazon.smithy.smithy.java.client-auth-api"

dependencies {
    api(project(":context"))
    api(project(":auth-api"))
    api(libs.smithy.model)
}
