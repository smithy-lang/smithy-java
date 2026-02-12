plugins {
    id("smithy-java.module-conventions")
}

description = "HPACK codec for HTTP/2 header compression"

extra["displayName"] = "Smithy :: Java :: HTTP :: HPACK"
extra["moduleName"] = "software.amazon.smithy.java.http.hpack"

dependencies {
    api(project(":http:http-api"))

    // Jackson for HPACK test suite JSON parsing
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}
