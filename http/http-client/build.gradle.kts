plugins {
    id("smithy-java.module-conventions")
    id("me.champeau.jmh") version "0.7.3"
}

description = "Smithy's generic blocking HTTP client with bidirectional streaming"

extra["displayName"] = "Smithy :: Java :: HTTP :: Client"
extra["moduleName"] = "software.amazon.smithy.java.http.client"

dependencies {
    api(project(":http:http-api"))
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    // profilers.add("async:output=flamegraph")
    // profilers.add("gc")
}
