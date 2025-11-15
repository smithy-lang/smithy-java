plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides client HTTP functionality using Netty"

extra["displayName"] = "Smithy :: Java :: Client :: Netty HTTP"
extra["moduleName"] = "software.amazon.smithy.java.client.netty.http"

dependencies {
    implementation(project(":logging"))
    implementation(project(":client:client-http")) // For HttpMessageExchange and HttpContext
    api(project(":client:client-core"))
    api(project(":http:http-api"))

    // Netty dependencies
    implementation(libs.netty.handler)
    implementation(libs.netty.common)
    implementation(libs.netty.buffer)
    implementation(libs.netty.codec)
    implementation(libs.netty.codec.http)
    implementation(libs.netty.codec.http2)

    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
    testImplementation(project(":aws:client:aws-client-awsjson"))
    testImplementation(project(":client:dynamic-client"))
}
