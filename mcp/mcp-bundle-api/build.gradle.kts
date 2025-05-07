plugins {
    application
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module implements the mcp-bundler utility"

extra["displayName"] = "Smithy :: Java :: MCP Bundler"
extra["moduleName"] = "software.amazon.smithy.java.mcp.bundle.api"

dependencies {
    smithyBuild(project(":codegen:plugins:types-codegen"))

    implementation(project(":core"))
    implementation(libs.smithy.model)
    api(project(":client:client-auth-api"))
    api(project(":server:server-api"))
    api(project(":client:client-core"))
    api(project(":dynamic-schemas"))
    implementation(project(":server:server-proxy"))
}

afterEvaluate {
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    sourceSets {
        main {
            java {
                srcDir(typePath)
                include("software/**")
            }
            resources {
                srcDir(typePath)
                include("META-INF/**")
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
