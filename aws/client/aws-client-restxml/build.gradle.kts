plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of AWS REST XML"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: REST XML"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.restxml"

dependencies {
    api(project(":client:client-http-binding"))
    api(project(":client:client-http"))
    api(project(":codecs:xml-codec"))
    api(project(":aws:aws-event-streams"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

protocolTestRuns {
    run("native") { systemProperty("smithy-java.xml-provider", "smithy") }
    run("stax") { }
    run("codegen") {
        systemProperty("smithy-java.xml-provider", "smithy")
        // Strict turns an emitter bug into a failure instead of a silent fall back to the
        // dispatch path, which would leave this run indistinguishable from "native".
        systemProperty("smithy-java.runtime-codegen.xml", "strict")
        systemProperty("smithy-java.runtime-codegen.http-binding", "strict")
    }
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "restXml", "aws.protocoltests.restxml#RestXml")
addGenerateSrcsTask(generator, "restXmlWithNamespace", "aws.protocoltests.restxml.xmlns#RestXmlWithNamespace")
