plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of the client RpcV2 JSON protocol"

extra["displayName"] = "Smithy :: Java :: Client :: RPCv2 JSON"
extra["moduleName"] = "software.amazon.smithy.java.client.rpcv2json"

dependencies {
    api(project(":client:client-rpcv2"))
    api(project(":codecs:json-codec", configuration = "shadow"))
    api(libs.smithy.aws.traits)

    implementation(libs.smithy.protocol.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "rpcv2Json", "smithy.protocoltests.rpcv2Json#RpcV2JsonProtocol")
