plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of AWS Query and EC2 Query protocols"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: AWS Query"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.awsquery"

dependencies {
    api(project(":client:client-http"))
    api(project(":codecs:xml-codec"))
    api(project(":io"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "awsQuery", "aws.protocoltests.query#AwsQuery")
addGenerateSrcsTask(generator, "ec2Query", "aws.protocoltests.ec2#AwsEc2")
