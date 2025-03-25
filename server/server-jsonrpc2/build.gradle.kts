plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides JSON-RPCv2 support for servers."

extra["displayName"] = "Smithy :: Java :: Server :: Protocols :: JSON-RPC V2"
extra["moduleName"] = "software.amazon.smithy.java.server.protocols.jsonrpc2"

dependencies {
    api(project(":server:server-core"))
    implementation(project(":jsonrpc2-schema"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(project(":codecs:json-codec"))
}
