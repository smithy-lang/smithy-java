plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.shadow)
}

description = "Runtime code generation framework for specialized codec serializers/deserializers"

extra["displayName"] = "Smithy :: Java :: Codec Codegen"
extra["moduleName"] = "software.amazon.smithy.java.codegen.rt"

val janinoCoords =
    libs.janino.compiler
        .get()
        .toString()
val janinoCommonsCoords =
    libs.janino.commons.compiler
        .get()
        .toString()

dependencies {
    api(project(":core"))
    compileOnly(libs.janino.compiler)
    compileOnly(libs.janino.commons.compiler)
    testRuntimeOnly(libs.janino.compiler)
    testRuntimeOnly(libs.janino.commons.compiler)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        configurations = listOf(project.configurations.compileClasspath.get())
        dependencies {
            include(dependency(janinoCoords))
            include(dependency(janinoCommonsCoords))
            relocate("org.codehaus.janino", "software.amazon.smithy.java.internal.shaded.org.codehaus.janino")
            relocate(
                "org.codehaus.commons",
                "software.amazon.smithy.java.internal.shaded.org.codehaus.commons",
            )
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
