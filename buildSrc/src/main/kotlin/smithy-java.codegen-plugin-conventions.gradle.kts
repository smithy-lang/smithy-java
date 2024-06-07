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

// Ignore generated generated code for formatter check
spotless {
    java {
        targetExclude("**/build/**/*.*")
    }
}

// Do not run spotbugs on integ tests
tasks["spotbugsIt"].enabled = false

val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()

// Add generated sources to integ test sources
project.the<SourceSetContainer>()["it"].java.srcDir(generatedSrcDir)
