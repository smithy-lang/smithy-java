plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.shadow)
}

description = "This module provides json functionality"

extra["displayName"] = "Smithy :: Java :: JSON"
extra["moduleName"] = "software.amazon.smithy.java.json"

dependencies {
    api(project(":core"))
    implementation(libs.jackson.core)
    shadow(project(":core"))
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()

        dependencies {
            relocate("com.fasterxml.jackson.core", "software.amazon.smithy.java.shaded.com.fasterxml.jackson.core")
            exclude(project(":core"))
        }
    }
    jar {
        finalizedBy(shadowJar)
    }
}
