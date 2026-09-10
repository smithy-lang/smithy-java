plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides Smithy Java http-binding functionality"

extra["displayName"] = "Smithy :: Java :: HTTP :: Binding"
extra["moduleName"] = "software.amazon.smithy.java.http.binding"

dependencies {
    api(project(":context"))
    api(project(":core"))
    api(project(":http:http-api"))
    implementation(project(":logging"))
    implementation(project(":codecs:codec-commons", configuration = "shadow"))

    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
    testImplementation(project(":codecs:xml-codec"))
}

val jdk25CodegenTest =
    tasks.register<Test>("jdk25CodegenTest") {
        description = "Run strict HTTP binding runtime-codegen coverage tests."
        group = "verification"

        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
            }
        systemProperty("smithy-java.runtime-codegen.json", "strict")
        systemProperty("smithy-java.runtime-codegen.xml", "strict")
        systemProperty("smithy-java.runtime-codegen.http-binding", "strict")
        systemProperty("smithy-java.json-provider", "smithy")
        systemProperty("smithy-java.xml-provider", "smithy")
        useJUnitPlatform {
            includeTags("runtime-codegen-strict")
        }
    }

val jdk25CodegenCompatibilityTest =
    tasks.register<Test>("jdk25CodegenCompatibilityTest") {
        description = "Run the HTTP binding test suite with runtime code generation and fallback enabled."
        group = "verification"

        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
            }
        systemProperty("smithy-java.runtime-codegen.json", "enabled")
        systemProperty("smithy-java.runtime-codegen.xml", "enabled")
        systemProperty("smithy-java.runtime-codegen.http-binding", "enabled")
        systemProperty("smithy-java.json-provider", "smithy")
        systemProperty("smithy-java.xml-provider", "smithy")
        useJUnitPlatform()
    }

tasks.named("check") {
    dependsOn(jdk25CodegenTest, jdk25CodegenCompatibilityTest)
}
