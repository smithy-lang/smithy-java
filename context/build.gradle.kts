plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.jmh-conventions")
}

description = "This module provides a typed identity based collection"

extra["displayName"] = "Smithy :: Java :: Context"
extra["moduleName"] = "software.amazon.smithy.java.context"

jmh {
    profilers.add("async:output=flamegraph")
}
