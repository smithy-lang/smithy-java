plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.jmh)
}

description = "This module provides SigV4 signing functionality"

extra["displayName"] = "Smithy :: Java :: AWS :: Client-Http"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.http"

dependencies {
    implementation(project(":auth-api"))
    // Provides sigv4 trait
    implementation(libs.smithy.aws.traits)
    implementation(project(":logging"))
    // Provides SmithyHttpRequest
    implementation(project(":http-api"))
    // Provides Datastream
    implementation(project(":core"))
    // Provides clientPlugin and associated annotations
    implementation(project(":client-core"))
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    //profilers = ['async:output=flamegraph', 'gc']
}
