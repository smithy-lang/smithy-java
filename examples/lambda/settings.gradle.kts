/**
 * Smithy Java AWS Lambda Endpoint
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

rootProject.name = "LambdaEndpoint"
