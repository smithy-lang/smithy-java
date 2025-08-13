import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ApacheLicenseResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ApacheNoticeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipOutputStream


plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.shadow)
}

description = "This module provides json functionality"

extra["displayName"] = "Smithy :: Java :: JSON"
extra["moduleName"] = "software.amazon.smithy.java.json"

dependencies {
    api(project(":core"))
    compileOnly(libs.jackson.core)
    testRuntimeOnly(libs.jackson.core)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        configurations = listOf(project.configurations.compileClasspath.get())
        dependencies {
            include(
                dependency(
                    libs.jackson.core
                        .get()
                        .toString(),
                ),
            )
            relocate("com.fasterxml.jackson.core", "software.amazon.smithy.java.internal.shaded.com.fasterxml.jackson.core")
        }
        exclude("META-INF/LICENSE")
        exclude("META-INF/NOTICE")
    }
    jar {
        finalizedBy(shadowJar)
    }
}

(components["shadow"] as AdhocComponentWithVariants).addVariantsFromConfiguration(configurations.apiElements.get()) {
}

configurePublishing {
    customComponent = components["shadow"] as SoftwareComponent
}
