/**
 * Basic usage of generated server stubs.
 */

pluginManagement {
    val smithyGradleVersion: String by settings
    val smithyJavaVersion: String by settings

    plugins {
        id("software.amazon.smithy.gradle.smithy-base").version(smithyGradleVersion)
        id("software.amazon.smithy.java.gradle.smithy-java").version(smithyJavaVersion)
        id("com.gradleup.shadow").version("8.3.5")
    }

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "SmithyJavaMCPServer"
