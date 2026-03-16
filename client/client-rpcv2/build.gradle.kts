plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the shared base implementation for client RpcV2 protocols"

extra["displayName"] = "Smithy :: Java :: Client :: RPCv2"
extra["moduleName"] = "software.amazon.smithy.java.client.rpcv2"

dependencies {
    api(project(":client:client-http"))
    api(project(":aws:aws-event-streams"))
}
