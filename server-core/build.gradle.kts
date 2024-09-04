plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the core server functionality"

extra["displayName"] = "Smithy :: Java :: Server Core"
extra["moduleName"] = "software.amazon.smithy.java.server-core"

dependencies {
    api(project(":server"))
    api(project(":http-api"))
    implementation(project(":context"))
    implementation(libs.smithy.model)
    implementation(project(":core"))
    implementation(project(":io"))
    implementation(project(":logging"))
}
