plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
    id("me.champeau.jmh") version "0.7.3"
    alias(libs.plugins.shadow)
}

description = "This module provides json functionality"

extra["displayName"] = "Smithy :: Java :: JSON"
extra["moduleName"] = "software.amazon.smithy.java.json"

dependencies {
    api(project(":core"))
    compileOnly(libs.jackson.core)
    testRuntimeOnly(libs.jackson.core)
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
            relocate("com.fasterxml.jackson.core", "software.amazon.smithy.java.internal.shaded.com.fasterxml.jackson.core")
        }
    }
    jar {
        enabled = false
    }
}

(components["shadow"] as AdhocComponentWithVariants).addVariantsFromConfiguration(configurations.apiElements.get()) {
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

jmh {
    warmupIterations = 3
    iterations = 3
    fork = 3
    // profilers.add("async:output=flamegraph")
    // profilers.add("gc")
}
