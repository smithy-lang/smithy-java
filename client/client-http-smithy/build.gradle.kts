plugins {
    id("smithy-java.module-conventions")
}

description = "Client transport using Smithy's native HTTP client with full HTTP/2 bidirectional streaming"

extra["displayName"] = "Smithy :: Java :: Client :: HTTP :: Smithy"
extra["moduleName"] = "software.amazon.smithy.java.client.http.smithy"

dependencies {
    api(project(":client:client-http"))
    api(project(":http:http-client"))
    implementation(project(":logging"))
}
