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
    api(libs.smithy.utils)
    implementation(project(":core"))
    compileOnly(libs.fastdoubleparser)
    testImplementation(sourceSets["jdk25"].output)
    testRuntimeOnly(libs.fastdoubleparser)
    "jdk25Implementation"(sourceSets.main.get().output)
    "jdk21TestImplementation"(sourceSets.main.get().output)
}

tasks.named<JavaCompile>("compileJdk25Java") {
    javaCompiler =
        javaToolchains.compilerFor {
            languageVersion = JavaLanguageVersion.of(25)
        }
    options.release.set(25)
}

tasks.named<Jar>("sourcesJar") {
    from("src/jdk25/java")
}

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
        from(sourceSets["jdk25"].output) {
            into("META-INF/versions/25")
        }
        manifest {
            attributes["Multi-Release"] = "true"
        }
        configurations = listOf(project.configurations.compileClasspath.get())
        dependencies {
            include(
                dependency(
                    libs.fastdoubleparser
                        .get()
                        .toString(),
                ),
            )
        }
        relocate("ch.randelshofer", "software.amazon.smithy.java.codecs.commons.internal.shaded.ch.randelshofer")
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

afterEvaluate {
    val shadowComponent = components["shadow"] as AdhocComponentWithVariants
    shadowComponent.addVariantsFromConfiguration(configurations.sourcesElements.get()) {
        mapToMavenScope("runtime")
    }
    shadowComponent.addVariantsFromConfiguration(configurations.javadocElements.get()) {
        mapToMavenScope("runtime")
    }
}
