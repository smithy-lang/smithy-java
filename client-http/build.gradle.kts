plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides client HTTP functionality"

extra["displayName"] = "Smithy :: Java :: Client Http"
extra["moduleName"] = "software.amazon.smithy.java.client-http"

dependencies {
    api(project(":client-core"))
    api(project(":http-api"))
    implementation(project(":logging"))

    testImplementation(project(":json-codec"))
    testImplementation(project(":aws:client-awsjson"))
    testImplementation(project(":dynamic-client"))
}
