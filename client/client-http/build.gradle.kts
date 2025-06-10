plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides client HTTP functionality"

extra["displayName"] = "Smithy :: Java :: Client :: HTTP"
extra["moduleName"] = "software.amazon.smithy.java.client.http"

dependencies {
    api(project(":client:client-core"))
    api(project(":http:http-api"))
    implementation(project(":logging"))

    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
    testImplementation(project(":aws:client:aws-client-awsjson"))
    testImplementation(project(":client:dynamic-client"))
}

tasks.withType<Test> {
    systemProperty("jdk.httpclient.allowRestrictedHeaders", "host")
}
