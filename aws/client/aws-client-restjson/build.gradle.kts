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
    api(project(":codecs:json-codec", configuration = "shadow"))
    api(project(":aws:aws-event-streams"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

protocolTestRuns {
    run("native") { systemProperty("smithy-java.json-provider", "smithy") }
    run("jackson") { systemProperty("smithy-java.json-provider", "jackson") }
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "restJson1", "aws.protocoltests.restjson#RestJson")
