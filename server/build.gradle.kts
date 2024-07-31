plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the public Server interface"

extra["displayName"] = "Smithy :: Java :: Server"
extra["moduleName"] = "software.amazon.smithy.java.server"

dependencies {
    implementation(project(":logging"))
    implementation(project(":core"))
}