plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of AWS REST XML"

extra["displayName"] = "Smithy :: Java :: Client AWS REST XML"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.restxml"

dependencies {
    api(project(":client:client-http-binding"))
    api(project(":client:client-http"))
    api(project(":codecs:xml-codec"))
    api(project(":aws:event-streams"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "restXml", "aws.protocoltests.restxml#RestXml")
