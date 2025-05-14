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
}

tasks.shadowJar {
    dependencies {
        include(
            dependency(
                libs.jackson.core
                    .get()
                    .toString(),
            ),
        )
        relocate("com.fasterxml.jackson.core", "software.amazon.smithy.java.internal.com.fasterxml.jackson.core")
    }
    archiveClassifier.set("")
    mergeServiceFiles()
}

(components["shadow"] as AdhocComponentWithVariants).addVariantsFromConfiguration(configurations.apiElements.get()) {
}

configurePublishing {
    customComponent = components["shadow"] as SoftwareComponent
}

tasks.jar {
    enabled = true
    dependsOn(tasks.shadowJar)
    outputs.files(
        tasks.shadowJar
            .get()
            .outputs.files,
    )
}

artifacts {
    configurations.archives
        .get()
        .artifacts
        .clear()
    archives(tasks.shadowJar.get())
}
