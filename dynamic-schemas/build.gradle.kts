plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.jmh)
}

description = "This module provides a way to dynamically create Smithy Java schemas from a model"

extra["displayName"] = "Smithy :: Java :: Dynamic Schemas"
extra["moduleName"] = "software.amazon.smithy.java.dynamicschemas"

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
}

dependencies {
    api(project(":core"))

    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
}
