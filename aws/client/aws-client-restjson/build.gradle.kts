plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of AWS REST JSON"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: REST JSON"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.restjson"

dependencies {
    api(project(":client:client-http-binding"))
    api(project(":client:client-http"))
    api(project(":codecs:json-codec"))
    api(project(":aws:aws-event-streams"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "restJson1", "aws.protocoltests.restjson#RestJson")
