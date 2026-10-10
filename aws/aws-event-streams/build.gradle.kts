plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.jmh-conventions")
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

    // Benchmarks reuse the hand-written event-stream fixtures in src/test (TestOperation, TestEventStream,
    // and the per-event structs) and drive the package-private encoder/decoder directly. The jmh source set
    // already includes the test source set and its classpath via the jmh plugin.
    jmhImplementation(project(":codecs:json-codec", configuration = "shadow"))
}
