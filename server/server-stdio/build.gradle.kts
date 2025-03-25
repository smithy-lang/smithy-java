plugins {
    id("smithy-java.module-conventions")
}

description =
    "Server that listens over an input stream and replies on an output stream. Can be attached to stdio or used for local testing."

extra["displayName"] = "Smithy :: Java :: IOStream Server"
extra["moduleName"] = "software.amazon.smithy.java.server.iostream"

dependencies {
    api(project(":server:server-core"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(project(":codecs:json-codec"))
    implementation(project(":jsonrpc2-schema"))
}
