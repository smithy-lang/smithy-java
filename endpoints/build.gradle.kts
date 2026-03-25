plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides endpoint resolution APIs for Smithy Java"

extra["displayName"] = "Smithy :: Java :: Endpoints"
extra["moduleName"] = "software.amazon.smithy.java.endpoints"

dependencies {
    api(project(":context"))
    api(project(":core"))
    api(libs.smithy.model)
}
