plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the proxy server functionality"

extra["displayName"] = "Smithy :: Java :: Proxy server"
extra["moduleName"] = "software.amazon.smithy.java.server.proxy"

dependencies {
    api(project(":server:server-api"))
    api(project(":http:http-api"))
    api(project(":core"))
    api(project(":context"))
    api(project(":framework-errors"))
    implementation(libs.smithy.model)
    implementation(project(":io"))
    implementation(project(":logging"))
    implementation(project(":dynamic-schemas"))
    implementation(project(":client:dynamic-client"))
    implementation(project(":client:client-core"))
    implementation(project(":aws:client:aws-client-http"))
}
