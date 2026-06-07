plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
    id("com.gradleup.shadow")
    id("smithy-java.jmh-conventions")
}

pitest {
    excludedClasses.add("software.amazon.smithy.java.codecs.commons.Schubfach*")
}

description = "Shared utilities for Smithy codec implementations (number formatting, timestamps, base64)"

extra["displayName"] = "Smithy :: Java :: Codec Commons"
extra["moduleName"] = "software.amazon.smithy.java.codecs.commons"

dependencies {
    api(libs.smithy.utils)
    compileOnly(libs.fastdoubleparser)
    testRuntimeOnly(libs.fastdoubleparser)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        configurations = listOf(project.configurations.compileClasspath.get())
        dependencies {
            include(
                dependency(
                    libs.fastdoubleparser
                        .get()
                        .toString(),
                ),
            )
            relocate("ch.randelshofer", "software.amazon.smithy.java.codecs.commons.internal.shaded.ch.randelshofer")
        }
    }
    jar {
        finalizedBy(shadowJar)
    }
}

configurations {
    shadow.get().extendsFrom(api.get())
}

configurePublishing {
    customComponent = components["shadow"] as SoftwareComponent
}

afterEvaluate {
    val shadowComponent = components["shadow"] as AdhocComponentWithVariants
    shadowComponent.addVariantsFromConfiguration(configurations.sourcesElements.get()) {
        mapToMavenScope("runtime")
    }
    shadowComponent.addVariantsFromConfiguration(configurations.javadocElements.get()) {
        mapToMavenScope("runtime")
    }
}
