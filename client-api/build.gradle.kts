plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides APIs needed for extending a client"

extra["displayName"] = "Smithy :: Java :: Client API"
extra["moduleName"] = "software.amazon.smithy.smithy.java.client-api"

dependencies {
    api(project(":context"))
    api(project(":core"))
    api(project(":client-endpoint-api"))
    implementation(project(":logging"))
}
