plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides mocking functionality for HTTP clients"

extra["displayName"] = "Smithy :: Java :: JSON"
extra["moduleName"] = "software.amazon.smithy.java.client.http.mock"

dependencies {
    implementation(project(":logging"))
    implementation(project(":core"))
    implementation(project(":client-core"))
    implementation(project(":client-http"))

    // Included to allow mocking responses based on shapes.
    implementation(project(":server-core"))

    testImplementation(project(":dynamic-client"))
    testImplementation(project(":aws:client-restjson"))
    testImplementation(project(":server-aws-rest-json1"))
}
