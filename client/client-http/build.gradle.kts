plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides client HTTP functionality"

extra["displayName"] = "Smithy :: Java :: Client Http"
extra["moduleName"] = "software.amazon.smithy.java.client.http"

dependencies {
    api(project(":client:client-core"))
    api(project(":http-api"))
    implementation(project(":logging"))

    testImplementation(project(":codecs:json-codec"))
    testImplementation(project(":aws:client:aws-client-awsjson"))
    testImplementation(project(":client:dynamic-client"))
}
