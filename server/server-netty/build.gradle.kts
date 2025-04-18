plugins {
    id("smithy-java.module-conventions")
}

description = "Netty based Smithy Java Server implementation"

extra["displayName"] = "Smithy :: Java :: Server :: Netty"
extra["moduleName"] = "software.amazon.smithy.java.server.netty"

dependencies {
    api(project(":server:server-core"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(libs.netty.all)
}
