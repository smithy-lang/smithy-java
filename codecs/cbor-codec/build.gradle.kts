plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
    id("software.amazon.smithy.gradle.smithy-base")
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
    smithyBuild(project(":codegen:codegen-plugin"))
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

tasks.named("compileTestJava") {
    dependsOn("smithyBuild")
}

tasks.named("processTestResources") {
    dependsOn("smithyBuild")
}

val jdk25CodegenTest =
    tasks.register<Test>("jdk25CodegenTest") {
        description = "Run the CBOR test suite with strict runtime code generation."
        group = "verification"

        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
            }
        systemProperty("smithy-java.runtime-codegen.cbor", "strict")
        useJUnitPlatform()
    }

tasks.named("check") {
    dependsOn(jdk25CodegenTest)
}
