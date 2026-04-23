plugins {
    id("smithy-java.module-conventions")
}

description = "Client transport using Apache HttpClient 5 async for HTTP/1.1 and HTTP/2"

extra["displayName"] = "Smithy :: Java :: Client :: HTTP :: Apache"
extra["moduleName"] = "software.amazon.smithy.java.client.http.apache"

dependencies {
    api(project(":client:client-http"))
    implementation(project(":logging"))

    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")

    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
}
