plugins {
    id("smithy-java.java-conventions")
}

description = "Shared, non-published utilities for smithy-java benchmarks."

dependencies {
    compileOnly(libs.jmh.core)
    testImplementation(libs.jmh.core)
}

// Shared with the e2e runner, so this intentionally keeps the repository's
// Java 21 release target even though serde benchmarks execute on JDK 25.

// Not published. No `smithy-java.module-conventions`, no publishing, no BOM entry.
