plugins {
    id("smithy-java.module-conventions")
}

description = "Client transport using AWS CRT for HTTP/1.1 and HTTP/2"

extra["displayName"] = "Smithy :: Java :: Client :: HTTP :: CRT"
extra["moduleName"] = "software.amazon.smithy.java.client.http.crt"

dependencies {
    api(project(":client:client-http"))
    implementation(project(":logging"))

    implementation("software.amazon.awssdk.crt:aws-crt:0.40.1")
}
