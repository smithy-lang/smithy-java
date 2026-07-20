/**
 * Basic usage of generated server stubs.
 */

pluginManagement {
    val smithyGradleVersion: String by settings

    plugins {
        id("software.amazon.smithy.gradle.smithy-base").version(smithyGradleVersion)
    }

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "BasicSmithyJavaServer"
