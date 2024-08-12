plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the implementation of AWS REST JSON 1.0"

extra["displayName"] = "Smithy :: Java :: Client AWS REST JSON 1.0"
extra["moduleName"] = "software.amazon.smithy.java.client-aws-rest-json1"

dependencies {
    api(project(":client-http"))
    api(project(":http-binding"))
    api(project(":json-codec"))
    api(project(":aws-event-streams"))
    api(libs.smithy.aws.traits)
}
