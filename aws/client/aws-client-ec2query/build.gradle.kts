plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of AWS EC2 Query protocol"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: EC2 Query"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.ec2query"

dependencies {
    api(project(":client:client-http"))
    api(project(":codecs:xml-codec"))
    api(project(":io"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "ec2Query", "aws.protocoltests.ec2#AwsEc2")
