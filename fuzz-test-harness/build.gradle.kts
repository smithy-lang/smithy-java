plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.integ-test-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module provides a test harness and tools for fuzzing Smithy codecs using Jazzer."

dependencies {
    smithyBuild(project(":codegen:codegen-plugin"))

    implementation(project(":core"))
    implementation(project(":logging"))
    implementation(project(":codecs:cbor-codec"))

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
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets {
        main {
            java {
                srcDir("$typePath/java")
            }
            resources {
                srcDir("$typePath/resources")
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

tasks.processResources {
    dependsOn("compileJava")
}
