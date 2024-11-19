plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides HTTP binding support for clients"

extra["displayName"] = "Smithy :: Java :: Client HTTP Binding"
extra["moduleName"] = "software.amazon.smithy.java.client.http.binding"

dependencies {
    api(project(":client-core"))
    api(project(":client-http"))
    api(project(":http-binding"))
    implementation(project(":logging"))

    testImplementation(project(":json-codec"))
}
