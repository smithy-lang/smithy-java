plugins {
    id("smithy-java.module-conventions")
    id("me.champeau.jmh") version "0.7.3"
}

description = "This module provides a typed identity based collection"

extra["displayName"] = "Smithy :: Java :: Context"
extra["moduleName"] = "software.amazon.smithy.java.context"

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    profilers.add("async:output=flamegraph")
    // profilers.add("gc")
    duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE // don't dump a bunch of warnings.
}
