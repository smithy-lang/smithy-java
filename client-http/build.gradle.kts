plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides client HTTP functionality"

extra["displayName"] = "Smithy :: Java :: Client Http"
extra["moduleName"] = "software.amazon.smithy.java.client-http"

dependencies {
    api(project(":http-binding"))
    api(project(":client-core"))
}
