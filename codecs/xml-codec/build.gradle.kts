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
