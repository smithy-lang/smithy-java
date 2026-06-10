plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides a dynamic Smithy client"

extra["displayName"] = "Smithy :: Java :: Dynamic client"
extra["moduleName"] = "software.amazon.smithy.java.dynamicclient"

dependencies {
    api(project(":dynamic-schemas"))
    api(project(":client:client-core"))
    implementation(project(":logging"))

    testImplementation(project(":client:client-rulesengine"))
    testImplementation(project(":aws:client:aws-client-restjson"))
    testImplementation(project(":aws:client:aws-client-awsjson"))
    testImplementation(project(":aws:aws-sigv4"))
    // Registers the aws.partition endpoint function used by real AWS service models (e.g. Connect's endpointBdd).
    testImplementation(libs.smithy.aws.endpoints)
}
