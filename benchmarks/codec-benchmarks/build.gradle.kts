plugins {
    id("smithy-java.java-conventions")
    alias(libs.plugins.jmh)
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "Codec benchmarks for Smithy Java serialization codecs"

dependencies {
    smithyBuild(project(":codegen:codegen-plugin"))
    // core provides smithy-model transitively, needed by smithy-base plugin to resolve CLI version
    implementation(project(":core"))
    jmhImplementation(project(":codecs:json-codec", configuration = "shadow"))
    jmhImplementation(project(":codecs:cbor-codec"))
    // Jackson classes needed for the reverseJsonFieldOrder helper in JsonBench
    jmhImplementation(libs.jackson.core)
}

afterEvaluate {
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets.named("jmh") {
        java {
            srcDir("$typePath/java")
        }
        resources {
            srcDir("$typePath/resources")
        }
    }
}

tasks.named("compileJmhJava") {
    dependsOn("smithyBuild")
}

tasks.named("processJmhResources") {
    dependsOn("smithyBuild")
}

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    jvmArgs.addAll("-Xms1g", "-Xmx1g")
    includes.addAll(
        providers
            .gradleProperty("jmh.includes")
            .map { listOf(it) }
            .orElse(emptyList()),
    )
    profilers.add("async:output=jfr;dir=${layout.buildDirectory.get()}/jmh-profiler")
}
