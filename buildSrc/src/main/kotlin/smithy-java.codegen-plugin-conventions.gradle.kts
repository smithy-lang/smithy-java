import org.gradle.api.Project

plugins {
    id("smithy-java.module-conventions")
}

// Workaround per: https://github.com/gradle/gradle/issues/15383
val Project.libs get() = the<org.gradle.accessors.dm.LibrariesForLibs>()

group = "software.amazon.smithy.java.codegen"

dependencies {
    implementation(libs.smithy.codegen)
    implementation(project(":core"))
    implementation(project(":codegen:core"))
}
