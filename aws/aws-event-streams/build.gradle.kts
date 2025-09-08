plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides AWS event streaming support"

extra["displayName"] = "Smithy :: Java :: AWS :: Event Streams"
extra["moduleName"] = "software.amazon.smithy.java.aws.events"

tasks.test {
    dependsOn(":codecs:json-codec:shadowJar")
}

dependencies {
    api(project(":core"))
    implementation(project(":logging"))
    api("software.amazon.eventstream:eventstream:1.0.1")
    testImplementation(project(":codecs:json-codec"))
}
