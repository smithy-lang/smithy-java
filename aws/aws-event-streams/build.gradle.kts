plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides AWS event streaming support"

extra["displayName"] = "Smithy :: Java :: AWS :: Event Streams"
extra["moduleName"] = "software.amazon.smithy.java.aws.events"

dependencies {
    api(project(":core"))
    api("software.amazon.eventstream:eventstream:1.0.1")
}
