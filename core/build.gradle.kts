plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.jmh)
}

description = "This module provides the core functionality for Smithy java"

extra["displayName"] = "Smithy :: Java :: Core"
extra["moduleName"] = "software.amazon.smithy.java.core"

dependencies {
    api(libs.smithy.model)
}

jmh {
    iterations = 10
    fork = 1
    //profilers = ['async:output=flamegraph', 'gc']
}
