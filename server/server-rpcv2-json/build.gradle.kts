plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the RpcV2 JSON support for servers."

extra["displayName"] = "Smithy :: Java :: Server :: RPCv2 JSON"
extra["moduleName"] = "software.amazon.smithy.java.server.rpcv2json"

dependencies {
    api(project(":server:server-rpcv2"))
    api(libs.smithy.protocol.traits)
    implementation(project(":codecs:json-codec", configuration = "shadow"))

    itImplementation(project(":server:server-api"))
    itImplementation(project(":server:server-netty"))
    itImplementation(project(":client:client-rpcv2-json"))

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
    testImplementation(libs.smithy.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "rpcv2Json", "smithy.protocoltests.rpcv2Json#RpcV2JsonProtocol", "server")
