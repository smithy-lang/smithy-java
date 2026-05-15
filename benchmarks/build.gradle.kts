/*
 * Aggregator project for benchmark modules.
 *
 * Benchmarks live under this directory and are NEVER published to Maven.
 * They exist to measure the runtime behavior of smithy-java components and
 * may depend on package-private internals or other smithy-java modules.
 *
 * Each benchmark module is a standalone subproject (typically using
 * `smithy-java.java-conventions` and the JMH plugin). See `serde-benchmarks`
 * for an example.
 */

// Apply the Java toolchain to all benchmark subprojects, but do NOT apply
// `smithy-java.module-conventions` (which would pull in publishing).
//
// Benchmarks target JDK 25 — the rest of smithy-java targets 21, but
// performance numbers should reflect the JIT/runtime improvements available
// to consumers running on the latest JVM. Subprojects that apply
// `smithy-java.java-conventions` will see that convention force 21; they
// MUST override `java.toolchain.languageVersion` and the `JavaCompile`
// `options.release` to 25 themselves (see `serde-benchmarks/build.gradle.kts`).
subprojects {
    plugins.withId("java") {
        the<JavaPluginExtension>().toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }
}
