plugins {
    id("smithy-java.module-conventions")
}

description = "Client transport using Netty for HTTP/1.1, HTTP/2, and HTTP/2 cleartext"

extra["displayName"] = "Smithy :: Java :: Client :: HTTP :: Netty"
extra["moduleName"] = "software.amazon.smithy.java.client.http.netty"

dependencies {
    api(project(":client:client-http"))
    implementation(project(":logging"))

    implementation("io.netty:netty-codec-http2:4.2.7.Final")
    implementation("io.netty:netty-codec-http:4.2.7.Final")
    implementation("io.netty:netty-handler:4.2.7.Final")
    implementation("io.netty:netty-buffer:4.2.7.Final")
    implementation("io.netty:netty-transport:4.2.7.Final")

    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
}
