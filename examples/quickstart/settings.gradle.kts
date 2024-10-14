rootProject.name = "smithy-java-quickstart"

pluginManagement {
    repositories {
        // Add plugin portal to download smithy gradle plugins
        gradlePluginPortal()
    }
}

// Subprojects
include("lib")
include("service")
include("client")
include("plugins")
