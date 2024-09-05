plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.protocol-testing-conventions")
}

description = "This module provides the implementation of AWS REST JSON 1.0"

extra["displayName"] = "Smithy :: Java :: Client AWS REST JSON 1.0"
extra["moduleName"] = "software.amazon.smithy.java.client-aws-rest-json1"

dependencies {
    api(project(":client-http"))
    api(project(":http-binding"))
    api(project(":json-codec"))
    api(project(":aws:event-streams"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)
}

val protocols = mapOf(Pair("restJson1", "aws.protocoltests.restjson#RestJson"))
val generator = "software.amazon.smithy.java.protocoltests.generators.ClientProtocolTestGenerator"
for ((name, service) in protocols) {
    addGenerateSrcsTask(generator, name, service)
}
