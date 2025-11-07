plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of the client RpcV2 CBOR protocol"

extra["displayName"] = "Smithy :: Java :: Client :: RPCv2 CBOR"
extra["moduleName"] = "software.amazon.smithy.java.client.rpcv2cbor"

dependencies {
    api(project(":client:client-http"))
    api(project(":codecs:cbor-codec"))
    api(project(":aws:aws-event-streams"))
    api(libs.smithy.aws.traits)

    implementation(project(":logging"))
    implementation(libs.smithy.protocol.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.protocol.tests)
    itImplementation(testFixtures(project(":codecs:cbor-codec")))
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "rpcv2Cbor", "smithy.protocoltests.rpcv2Cbor#RpcV2Protocol")
