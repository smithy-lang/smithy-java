plugins {
    id("smithy-java.module-conventions")
}

description = "Client transport using Apache HttpClient 5 Classic (blocking) for HTTP/1.1"

extra["displayName"] = "Smithy :: Java :: Client :: HTTP :: Apache Classic"
extra["moduleName"] = "software.amazon.smithy.java.client.http.apache.classic"

dependencies {
    api(project(":client:client-http"))
    implementation(project(":logging"))

    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")
}
