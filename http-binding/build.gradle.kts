plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides http-binding functionality"

extra["displayName"] = "Smithy :: Java :: Http Binding"
extra["moduleName"] = "software.amazon.smithy.java.http.binding"

dependencies {
    api(project(":core"))
    api(project(":http-api"))
    implementation(project(":logging"))
}
