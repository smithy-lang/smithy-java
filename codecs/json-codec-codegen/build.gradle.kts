plugins {
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "Runtime code generation for specialized JSON serializers/deserializers"

extra["displayName"] = "Smithy :: Java :: JSON Codec Codegen"
extra["moduleName"] = "software.amazon.smithy.java.json.codegen"

dependencies {
    api(project(":codecs:codec-codegen", configuration = "shadow"))
    api(project(":codecs:json-codec", configuration = "shadow"))
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
