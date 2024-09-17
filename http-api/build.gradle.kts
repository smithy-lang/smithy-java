plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the Smithy Java HTTP API"

extra["displayName"] = "Smithy :: Java :: HTTP"
extra["moduleName"] = "software.amazon.smithy.java.http-api"

dependencies {
    api(project(":io"))
}
