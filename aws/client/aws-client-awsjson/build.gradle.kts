plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of AWS JSON protocols"

extra["displayName"] = "Smithy :: Java :: Client AWS JSON 1.0 and 1.1"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.awsjson"

dependencies {
    api(project(":client:client-http"))
    api(project(":codecs:json-codec"))
    api(project(":aws:aws-event-streams"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "awsJson1_0", "aws.protocoltests.json10#JsonRpc10")
