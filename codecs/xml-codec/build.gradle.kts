plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module provides XML functionality"

extra["displayName"] = "Smithy :: Java :: XML"
extra["moduleName"] = "software.amazon.smithy.java.xml"

dependencies {
    api(project(":core"))
    api(project(":codecs:codec-commons", configuration = "shadow"))
    smithyBuild(project(":codegen:codegen-plugin"))
}

tasks.named<Test>("test") {
    systemProperty("smithy-java.xml-provider", "smithy")
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
        description = "Run the XML test suite with runtime code generation enabled."
        group = "verification"

        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
            }
        // Strict rather than enabled: a bug in the emitter is otherwise indistinguishable from a shape
        // the backend declines to support — both fall back to the dispatch path and the suite passes
        // having proved nothing.
        systemProperty("smithy-java.runtime-codegen.xml", "strict")
        systemProperty("smithy-java.xml-provider", "smithy")
        useJUnitPlatform()
    }

tasks.named("check") {
    dependsOn(jdk25CodegenTest)
}
