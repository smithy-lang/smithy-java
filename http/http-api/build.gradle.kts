plugins {
    id("smithy-java.module-conventions")
    id("me.champeau.jmh") version "0.7.3"
}

description = "This module provides the Smithy Java HTTP API"

extra["displayName"] = "Smithy :: Java :: HTTP :: API"
extra["moduleName"] = "software.amazon.smithy.java.http.api"

dependencies {
    api(project(":io"))
}

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE
    includes.addAll(
        providers
            .gradleProperty("jmh.includes")
            .map { listOf(it) }
            .orElse(emptyList()),
    )
}
