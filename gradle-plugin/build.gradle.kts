import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `java-gradle-plugin`
    `java-library`
    `maven-publish`
    signing
}

val smithyJavaVersion = file("../VERSION").readText().trim()

group = "software.amazon.smithy.java"
version = smithyJavaVersion
description = "Gradle plugin for Smithy Java code generation"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

// Versions are pinned here because this module is an included build (standalone settings.gradle.kts)
// and cannot access the parent project's version catalog. Update these when bumping smithy versions.
dependencies {
    implementation("software.amazon.smithy.gradle:smithy-base:1.4.0")
    implementation("software.amazon.smithy:smithy-model:1.72.0")
}

gradlePlugin {
    plugins {
        create("smithy-java") {
            id = "software.amazon.smithy.java.gradle.smithy-java"
            displayName = "Smithy Java Plugin"
            description = project.description
            implementationClass = "software.amazon.smithy.java.gradle.SmithyJavaPlugin"
            tags.addAll("smithy", "codegen", "java")
        }
    }
}

tasks.withType<ProcessResources> {
    filter<ReplaceTokens>("tokens" to mapOf("SmithyJavaVersion" to version.toString()))
}

publishing {
    repositories {
        maven {
            name = "stagingRepository"
            url = file("../build/staging").toURI()
        }
    }
    publications.withType<MavenPublication> {
        pom {
            name.set("Smithy :: Java :: Gradle Plugin")
            description.set(project.description)
            url.set("https://github.com/smithy-lang/smithy-java")
            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("smithy")
                    name.set("Smithy")
                    organization.set("Amazon Web Services")
                    organizationUrl.set("https://aws.amazon.com")
                    roles.add("developer")
                }
            }
            scm {
                url.set("https://github.com/smithy-lang/smithy-java.git")
            }
        }
    }
}

signing {
    setRequired {
        gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
    }
    if (project.hasProperty("signingKey") && project.hasProperty("signingPassword")) {
        useInMemoryPgpKeys(
            project.properties["signingKey"].toString(),
            project.properties["signingPassword"].toString(),
        )
        sign(publishing.publications)
    }
}
