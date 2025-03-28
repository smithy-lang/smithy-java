plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides AWS specific HTTP support"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: HTTP"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.http"

dependencies {
    api(project(":aws:client:aws-client-core"))
    api(project(":client:client-http"))
    api(project(":http:http-api"))

    testImplementation(project(":client:dynamic-client"))
    testImplementation(project(":client:mock-client-plugin"))
    testImplementation(project(":aws:client:aws-client-awsjson"))
}
