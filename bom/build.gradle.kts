plugins {
    `java-platform`
    id("smithy-java.publishing-conventions")
}

description = "Smithy Java BOM (Bill of Materials) for dependency version management"

extra["displayName"] = "Smithy :: Java :: BOM"

configurePublishing {
    customComponent = components["javaPlatform"]
}

// Auto-discover all published subprojects (those with maven-publish plugin)
// and add them as BOM constraints.
gradle.projectsEvaluated {
    dependencies {
        constraints {
            rootProject.subprojects
                .filter { it != project && it.plugins.hasPlugin("maven-publish")  }
                .sortedBy { it.path }
                .forEach { api(it) }
        }
    }
}