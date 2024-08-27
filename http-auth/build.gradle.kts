plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the Smithy Java HTTP Auth schemes"

extra["displayName"] = "Smithy :: Java :: HTTP :: Auth"
extra["moduleName"] = "software.amazon.smithy.java.http-auth"

dependencies {
    implementation(project(":auth-api"))
    implementation(project(":http-api"))
    implementation(project(":logging"))
    // for Query param builder
    implementation(project(":core"))
}
