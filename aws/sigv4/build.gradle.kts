plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.jmh)
}

description = "This module provides AWS-Specific http client functionality"

extra["displayName"] = "Smithy :: Java :: AWS :: Client-HTTP"
extra["moduleName"] = "software.amazon.smithy.java.aws.client-http"

dependencies {
    implementation(project(":client-core"))
    api(project(":aws:aws-client-core"))
    implementation(project(":http-api"))
    implementation(project(":io"))
    implementation(project(":logging"))
    implementation(libs.smithy.aws.traits)
}

jmh {
    iterations = 3
    warmupIterations = 2
    fork = 1
    // profilers.add("async:output=flamegraph")
    // profilers.add("gc")
}
