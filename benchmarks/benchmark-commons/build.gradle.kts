plugins {
    id("smithy-java.java-conventions")
}

description = "Shared, non-published utilities for smithy-java benchmarks."

dependencies {
    api(project(":core"))
    api(libs.smithy.protocol.test.traits)
    compileOnly(libs.jmh.core)
    testImplementation(libs.jmh.core)
}
