plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of the client RpcV2 CBOR protocol"

extra["displayName"] = "Smithy :: Java :: Client RpcV2 CBOR"
extra["moduleName"] = "software.amazon.smithy.java.aws.client-rpcv2-cbor-protocol"

dependencies {
    api(project(":client-http"))
    api(project(":rpcv2-cbor-codec"))
    api(libs.smithy.aws.traits)

    implementation(libs.smithy.protocol.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.protocol.tests)
    itImplementation(testFixtures(project(":rpcv2-cbor-codec")))
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "rpcv2Cbor", "smithy.protocoltests.rpcv2Cbor#RpcV2Protocol")
