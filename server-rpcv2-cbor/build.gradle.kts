plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the RpcV2 CBOR support for servers."

extra["displayName"] = "Smithy :: Java :: Server RPCV2 CBOR"
extra["moduleName"] = "software.amazon.smithy.java.server-rpcV2-cbor"

dependencies {
    api(project(":server-api"))
    api(libs.smithy.protocol.traits)
    implementation(project(":server-core"))
    implementation(project(":context"))
    implementation(project(":core"))
    implementation(project(":rpcv2-cbor-codec"))

    itImplementation(project(":server-api"))
    itImplementation(project(":server-netty"))
    itImplementation(project(":aws:client-rpcv2-cbor-protocol"))
    itImplementation(testFixtures(project(":rpcv2-cbor-codec")))

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
    testImplementation(libs.smithy.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "rpcv2Cbor", "smithy.protocoltests.rpcv2Cbor#RpcV2Protocol", "server")
