plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides a dynamic Smithy client"

extra["displayName"] = "Smithy :: Java :: Dynamic client"
extra["moduleName"] = "software.amazon.smithy.java.dynamicclient"

dependencies {
    api(project(":client-core"))

    testImplementation(project(":aws:client-restjson"))
    testImplementation(project(":aws:client-awsjson"))
    testImplementation(project(":aws:sigv4"))
}
