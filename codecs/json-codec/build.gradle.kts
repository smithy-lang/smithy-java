plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
    id("software.amazon.smithy.gradle.smithy-base")
    id("com.gradleup.shadow")
}

description = "This module provides json functionality"

extra["displayName"] = "Smithy :: Java :: JSON"
extra["moduleName"] = "software.amazon.smithy.java.json"

dependencies {
    api(project(":core"))
    api(project(":codecs:codec-commons", configuration = "shadow"))
    compileOnly(libs.jackson.core)
    testRuntimeOnly(libs.jackson.core)
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
            relocate("tools.jackson.core", "software.amazon.smithy.java.internal.shaded.tools.jackson.core")
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
    sourceSets.named("test") {
        java {
            srcDir("$typePath/java")
        }
        resources {
            srcDir("$typePath/resources")
        }
    }
}

tasks.named("compileTestJava") {
    dependsOn("smithyBuild")
}

tasks.named("processTestResources") {
    dependsOn("smithyBuild")
}
