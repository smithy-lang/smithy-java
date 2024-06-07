import org.gradle.api.Project

plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.integ-test-conventions")
}

// Workaround per: https://github.com/gradle/gradle/issues/15383
val Project.libs get() = the<org.gradle.accessors.dm.LibrariesForLibs>()

group = "software.amazon.smithy.java.codegen"

dependencies {
    implementation(libs.smithy.codegen)
    implementation(project(":core"))

    // Avoid circular dependency in codegen core
    if (project.name != "core") {
        implementation(project(":codegen:core"))
    }
}

// Do not run spotbugs on integration tests
tasks.named("spotbugsIt") {
    enabled = false
}

val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()

// Add generated sources to integration test sources
sourceSets {
    named("it") {
        java {
            srcDir(generatedSrcDir)
        }
    }
}
