

plugins {
    id("smithy-java.module-conventions")
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
        finalizedBy(shadowJar)
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
