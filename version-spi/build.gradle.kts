plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.publishing-conventions")
}

description = "This module provides the SPI for Smithy Java version compatibility checks"

extra["displayName"] = "Smithy :: Java :: Version SPI"
extra["moduleName"] = "software.amazon.smithy.java.versionspi"

group = "software.amazon.smithy.java"
version = project.file("${project.rootDir}/VERSION").readText().replace(System.lineSeparator(), "")

// This module doesn't apply smithy-java.module-conventions, which names the other modules.
tasks.jar {
    manifest {
        attributes(mapOf("Automatic-Module-Name" to project.extra["moduleName"]))
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}
