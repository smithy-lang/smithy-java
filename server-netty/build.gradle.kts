plugins {
    id("smithy-java.module-conventions")
}

description = "Netty based Smithy Java Server implementation"

extra["displayName"] = "Smithy :: Java :: Server Netty"
extra["moduleName"] = "software.amazon.smithy.java.server.netty"

dependencies {
    implementation(project(":server-core"))
    implementation(project(":logging"))
    implementation(libs.netty.all)
}
