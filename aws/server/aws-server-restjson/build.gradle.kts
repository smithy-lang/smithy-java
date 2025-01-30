plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the AWS RestJson1 support for servers."

extra["displayName"] = "Smithy :: Java :: Server AWS RestJson1"
extra["moduleName"] = "software.amazon.smithy.java.server-aws-rest-json1"

dependencies {
    api(project(":server:server-api"))
    api(project(":http-api"))
    api(libs.smithy.aws.traits)
    implementation(project(":server:server-core"))
    implementation(project(":context"))
    implementation(project(":core"))
    implementation(project(":codecs:json-codec"))
    implementation(project(":http-binding"))

    itImplementation(project(":server:server-api"))
    itImplementation(project(":server:server-netty"))
    itImplementation(project(":aws:client:aws-client-restjson"))
    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "restJson1", "aws.protocoltests.restjson#RestJson", "server")
