plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the RpcV2 CBOR support for servers."

extra["displayName"] = "Smithy :: Java :: Server :: Protocols :: RPCV2 CBOR"
extra["moduleName"] = "software.amazon.smithy.java.server.protocols.rpcv2cbor"

dependencies {
    api(project(":server:server-api"))
    api(libs.smithy.protocol.traits)
    implementation(project(":server:server-core"))
    implementation(project(":context"))
    implementation(project(":core"))
    implementation(project(":codecs:cbor-codec"))

    itImplementation(project(":server:server-api"))
    itImplementation(project(":server:server-netty"))
    itImplementation(project(":client:client-rpcv2-cbor"))
    itImplementation(testFixtures(project(":codecs:cbor-codec")))

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
    testImplementation(libs.smithy.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "rpcv2Cbor", "smithy.protocoltests.rpcv2Cbor#RpcV2Protocol", "server")
