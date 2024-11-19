plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of AWS REST JSON"

extra["displayName"] = "Smithy :: Java :: Client AWS REST JSON"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.restjson"

dependencies {
    api(project(":client-http-binding"))
    api(project(":client-http"))
    api(project(":json-codec"))
    api(project(":aws:event-streams"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "restJson1", "aws.protocoltests.restjson#RestJson")
