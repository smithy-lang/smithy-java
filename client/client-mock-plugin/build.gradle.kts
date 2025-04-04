plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides mocking functionality for HTTP clients"

extra["displayName"] = "Smithy :: Java :: Client :: HTTP :: Mock"
extra["moduleName"] = "software.amazon.smithy.java.client.http.mock"

dependencies {
    implementation(project(":logging"))
    implementation(project(":core"))
    implementation(project(":client:client-core"))
    implementation(project(":client:client-http"))

    // Included to allow mocking responses based on shapes.
    implementation(project(":server:server-core"))

    testImplementation(project(":client:dynamic-client"))
    testImplementation(project(":aws:client:aws-client-restjson"))
    testImplementation(project(":aws:server:aws-server-restjson"))
}
