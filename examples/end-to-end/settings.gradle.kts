/**
 * Example combining client and server into a single end-to-end example.
 */

pluginManagement {
    val smithyGradleVersion: String by settings
    val smithyJavaVersion: String by settings

    plugins {
        id("software.amazon.smithy.gradle.smithy-base").version(smithyGradleVersion)
        id("software.amazon.smithy.java.gradle.smithy-java").version(smithyJavaVersion)
    }

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "End-To-End"
