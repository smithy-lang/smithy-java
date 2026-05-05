plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the shared base implementation for server RpcV2 protocols"

extra["displayName"] = "Smithy :: Java :: Server :: RPCv2"
extra["moduleName"] = "software.amazon.smithy.java.server.rpcv2"

dependencies {
    api(project(":server:server-api"))
    api(project(":server:server-core"))
}
