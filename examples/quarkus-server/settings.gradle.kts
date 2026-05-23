/**
 * Example showing the user-facing experience for a Smithy-Java server inside
 * Quarkus, using the experimental `quarkus-smithy` extension. The extension
 * owns codegen (no smithy-base needed) and the Server lifecycle (no manual
 * StartupEvent observer needed).
 */

pluginManagement {
    val quarkusPluginVersion: String by settings

    plugins {
        id("io.quarkus").version(quarkusPluginVersion)
    }

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Quarkus-Server"
