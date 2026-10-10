plugins {
    id("smithy-java.java-conventions")
}

description = "Shared, non-published utilities for smithy-java benchmarks."

dependencies {
    compileOnly(libs.jmh.core)
    testImplementation(libs.jmh.core)
}

// Keep Java 21 compatibility for the e2e and live runners.
