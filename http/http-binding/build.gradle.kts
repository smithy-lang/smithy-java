plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides Smithy Java http-binding functionality"

extra["displayName"] = "Smithy :: Java :: HTTP :: Binding"
extra["moduleName"] = "software.amazon.smithy.java.http.binding"

dependencies {
    api(project(":core"))
    api(project(":http:http-api"))
    implementation(project(":logging"))
}
