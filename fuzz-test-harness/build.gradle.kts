plugins {
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module provides a test harness and tools for fuzzing Smithy codecs using Jazzer."

extra["displayName"] = "Smithy :: Java :: Fuzz Test Harness"
extra["moduleName"] = "software.amazon.smithy.java.fuzz"

dependencies {
    smithyBuild(project(":codegen:plugins:types-codegen"))

    implementation(project(":core"))
    implementation(project(":logging"))

    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.engine)
    api(libs.junit.jupiter.params)

    // Jazzer for fuzz testing
    api(libs.jazzer.junit)
    api(libs.jazzer.api)

    implementation(libs.assertj.core)
}

afterEvaluate {
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    sourceSets {
        main {
            java {
                srcDir(typePath)
                include("software/**")
            }
            resources {
                srcDir(typePath)
                include("META-INF/**")
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

tasks.sourcesJar {
    mustRunAfter("compileJava")
}

tasks.processResources {
    dependsOn("compileJava")
}
