plugins {
    id("smithy-java.java-conventions")
    alias(libs.plugins.jmh)
    id("software.amazon.smithy.gradle.smithy-base")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(25)
}

description = "Codec benchmarks for Smithy Java serialization codecs"

dependencies {
    smithyBuild(project(":codegen:codegen-plugin"))
    // core provides smithy-model transitively, needed by smithy-base plugin to resolve CLI version
    implementation(project(":core"))
    jmhImplementation(project(":codecs:json-codec", configuration = "shadow"))
    jmhImplementation(project(":codecs:cbor-codec"))
    jmhImplementation(project(":codecs:codec-codegen"))
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
    val fast = providers.gradleProperty("jmh.fast").isPresent
    warmupIterations = if (fast) 2 else 3
    iterations = if (fast) 3 else 5
    warmup = if (fast) "3s" else "10s"
    timeOnIteration = if (fast) "3s" else "10s"
    fork = 1
    jvmArgs.addAll("-Xms1g", "-Xmx1g")
    jvmArgs.addAll(
        providers
            .gradleProperty("jmh.jitlog")
            .map {
                listOf(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+LogCompilation",
                    "-XX:LogFile=build/jmh-jitlog/hotspot.log",
                )
            }.orElse(emptyList()),
    )
    includes.addAll(
        providers
            .gradleProperty("jmh.includes")
            .map { listOf(it) }
            .orElse(emptyList()),
    )
    if (!fast) {
        profilers.add("async:output=jfr;dir=${layout.buildDirectory.get()}/jmh-profiler")
    }
}
