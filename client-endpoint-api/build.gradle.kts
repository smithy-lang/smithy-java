plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides endpoint resolver API"

extra["displayName"] = "Smithy :: Java :: Endpoint Resolver API"
extra["moduleName"] = "software.amazon.smithy.smithy.java.client-endpoint-api"

dependencies {
    api(project(":context"))
}
