plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides AWS specific HTTP support"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: HTTP"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.http"

dependencies {
    api(project(":aws:aws-client-core"))
    api(project(":client-http"))
    api(project(":http-api"))

    testImplementation(project(":dynamic-client"))
    testImplementation(project(":aws:client-awsjson"))
}
