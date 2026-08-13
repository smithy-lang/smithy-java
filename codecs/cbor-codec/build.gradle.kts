plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
    `java-test-fixtures`
}

description = "This module provides CBOR functionality"

extra["displayName"] = "Smithy :: Java :: CBOR"
extra["moduleName"] = "software.amazon.smithy.java.cbor"

dependencies {
    api(project(":core"))
    implementation(project(":codecs:codec-commons", configuration = "shadow"))
    testFixturesImplementation(libs.assertj.core)
    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
}

tasks.register<Test>("jdk24CodegenTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(24)
        }
    filter {
        includeTestsMatching("*RuntimeCborCodegenTest")
    }
    useJUnitPlatform()
}
