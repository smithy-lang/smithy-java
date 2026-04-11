plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
    id("me.champeau.jmh") version "0.7.3"
    id("software.amazon.smithy.gradle.smithy-base")
    alias(libs.plugins.shadow)
}

description = "This module provides json functionality"

extra["displayName"] = "Smithy :: Java :: JSON"
extra["moduleName"] = "software.amazon.smithy.java.json"

dependencies {
    api(project(":core"))
    compileOnly(libs.jackson.core)
    compileOnly(libs.fastdoubleparser)
    testRuntimeOnly(libs.jackson.core)
    testRuntimeOnly(libs.fastdoubleparser)
    smithyBuild(project(":codegen:codegen-plugin"))
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        configurations = listOf(project.configurations.compileClasspath.get())
        dependencies {
            include(
                dependency(
                    libs.jackson.core
                        .get()
                        .toString(),
                ),
            )
            include(
                dependency(
                    libs.fastdoubleparser
                        .get()
                        .toString(),
                ),
            )
            relocate("tools.jackson.core", "software.amazon.smithy.java.internal.shaded.tools.jackson.core")
            relocate("ch.randelshofer", "software.amazon.smithy.java.internal.shaded.ch.randelshofer")
        }
    }
    jar {
        finalizedBy(shadowJar)
    }
}

configurations {
    shadow.get().extendsFrom(api.get())
}

configurePublishing {
    customComponent = components["shadow"] as SoftwareComponent
}

// Ensure sources and javadocs jars are included in shadow component
afterEvaluate {
    val shadowComponent = components["shadow"] as AdhocComponentWithVariants
    shadowComponent.addVariantsFromConfiguration(configurations.sourcesElements.get()) {
        mapToMavenScope("runtime")
    }
    shadowComponent.addVariantsFromConfiguration(configurations.javadocElements.get()) {
        mapToMavenScope("runtime")
    }
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
    sourceSets.named("test") {
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

tasks.named("compileTestJava") {
    dependsOn("smithyBuild")
}

tasks.named("processJmhResources") {
    dependsOn("smithyBuild")
}

tasks.named("processTestResources") {
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
    // profilers.add("gc")
}
