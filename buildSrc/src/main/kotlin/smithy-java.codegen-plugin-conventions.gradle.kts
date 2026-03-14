import org.gradle.api.Project

plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.integ-test-conventions")
}

// Workaround per: https://github.com/gradle/gradle/issues/15383
val Project.libs get() = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(libs.smithy.codegen)
    implementation(project(":core"))
    implementation(project(":logging"))

    // Avoid circular dependency in codegen plugin
    if (project.name != "codegen-plugin") {
        api(project(":codegen:codegen-plugin"))
    }
}

val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()

// Add generated sources to integration test sources
sourceSets {
    named("it") {
        java {
            srcDir("$generatedSrcDir/java")
        }
        resources {
            srcDir("$generatedSrcDir/resources")
        }
    }
}

// Ensure integ tests are executed as part of test suite
tasks["test"].finalizedBy("integ")
