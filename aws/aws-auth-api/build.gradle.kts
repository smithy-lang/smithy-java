plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the Smithy Java AWS Auth API"

extra["displayName"] = "Smithy :: Java :: AWS :: Auth :: API"
extra["moduleName"] = "software.amazon.smithy.java.aws.auth.api"

dependencies {
    api(project(":auth-api"))
}
