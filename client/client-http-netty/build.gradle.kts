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

    testImplementation("org.slf4j:slf4j-simple:2.0.17")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.78")
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.78")
}
