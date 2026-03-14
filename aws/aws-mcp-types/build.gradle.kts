plugins {
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module provides generic request parameters for interacting with AWS over MCP"
extra["displayName"] = "Smithy :: Java :: AWS MCP types"
extra["moduleName"] = "software.amazon.smithy.java.awsmcp.types"

dependencies {
    smithyBuild(project(":codegen:codegen-plugin"))
    api(project(":core"))
    api(libs.smithy.model)
}

afterEvaluate {
    val typesPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets {
        main {
            java {
                srcDir("$typesPath/java")
            }
            resources {
                srcDir("$typesPath/resources")
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

// Needed because sources-jar needs to run after smithy-build is done
tasks.sourcesJar {
    mustRunAfter("compileJava")
}

tasks.processResources {
    dependsOn("compileJava")
}
