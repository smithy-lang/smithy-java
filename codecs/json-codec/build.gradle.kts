plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
    id("software.amazon.smithy.gradle.smithy-base")
    alias(libs.plugins.shadow)
}

description = "This module provides json functionality"

extra["displayName"] = "Smithy :: Java :: JSON"
extra["moduleName"] = "software.amazon.smithy.java.json"

sourceSets {
    create("jdk25") {
        java {
            srcDir("src/jdk25/java")
        }
    }
    create("jdk21Test") {
        java {
            srcDir("src/jdk21Test/java")
        }
    }
}

dependencies {
    api(project(":core"))
    implementation(project(":codecs:codec-codegen"))
    compileOnly(libs.jackson.core)
    compileOnly(libs.fastdoubleparser)
    testImplementation(project(":codecs:codec-codegen"))
    testImplementation(sourceSets["jdk25"].output)
    testImplementation(libs.vineflower)
    testRuntimeOnly(libs.jackson.core)
    testRuntimeOnly(libs.fastdoubleparser)
    smithyBuild(project(":codegen:codegen-plugin"))
    "jdk25Implementation"(project(":codecs:codec-codegen"))
    "jdk25Implementation"(sourceSets.main.get().output)
    "jdk21TestImplementation"(sourceSets["jdk25"].output)
}

tasks.named<JavaCompile>("compileJdk25Java") {
    javaCompiler =
        javaToolchains.compilerFor {
            languageVersion = JavaLanguageVersion.of(25)
        }
    options.release.set(25)
}

// Compile and run main tests on JDK 25 so codegen serde provider is exercised
tasks.named<JavaCompile>("compileTestJava") {
    dependsOn("smithyBuild")
    javaCompiler =
        javaToolchains.compilerFor {
            languageVersion = JavaLanguageVersion.of(25)
        }
    options.release.set(25)
}

tasks.named<Test>("test") {
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
        }
}

// JDK 21 test to verify graceful fallback when codegen is unavailable
tasks.register<Test>("jdk21Test") {
    testClassesDirs = sourceSets["jdk21Test"].output.classesDirs
    classpath = sourceSets["jdk21Test"].runtimeClasspath
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
        }
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("jdk21Test")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        from(sourceSets["jdk25"].output)
        configurations = listOf(project.configurations.compileClasspath.get())
        dependencies {
            include(
                dependency(
                    libs.jackson.core
                        .get()
                        .toString(),
                ),
            )
            include(
                dependency(
                    libs.fastdoubleparser
                        .get()
                        .toString(),
                ),
            )
            relocate("tools.jackson.core", "software.amazon.smithy.java.internal.shaded.tools.jackson.core")
            relocate("ch.randelshofer", "software.amazon.smithy.java.internal.shaded.ch.randelshofer")
        }
    }
    jar {
        finalizedBy(shadowJar)
    }
}

configurations {
    shadow.get().extendsFrom(api.get())
    named("jdk25Implementation") {
        extendsFrom(configurations.implementation.get())
    }
    named("jdk21TestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    named("jdk21TestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
}

configurePublishing {
    customComponent = components["shadow"] as SoftwareComponent
}

// Ensure sources and javadocs jars are included in shadow component
afterEvaluate {
    val shadowComponent = components["shadow"] as AdhocComponentWithVariants
    shadowComponent.addVariantsFromConfiguration(configurations.sourcesElements.get()) {
        mapToMavenScope("runtime")
    }
    shadowComponent.addVariantsFromConfiguration(configurations.javadocElements.get()) {
        mapToMavenScope("runtime")
    }
}

afterEvaluate {
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets.named("test") {
        java {
            srcDir("$typePath/java")
        }
        resources {
            srcDir("$typePath/resources")
        }
    }
}

tasks.named("processTestResources") {
    dependsOn("smithyBuild")
}
