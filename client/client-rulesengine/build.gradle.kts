plugins {
    id("smithy-java.module-conventions")
    id("me.champeau.jmh") version "0.7.3"
}

description = "Implements the rules engine traits used to resolve endpoints"

extra["displayName"] = "Smithy :: Java :: Client :: Endpoint Rules"
extra["moduleName"] = "software.amazon.smithy.java.client.endpointrules"

dependencies {
    api(project(":client:client-core"))
    implementation(libs.smithy.rules)
    implementation(project(":logging"))
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    // profilers.add("async:output=flamegraph")
    // profilers.add("gc")
    duplicateClassesStrategy = DuplicatesStrategy.WARN
}
