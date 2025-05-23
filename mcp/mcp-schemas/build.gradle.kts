plugins {
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module provides a schemas for MCP integration"

extra["displayName"] = "Smithy :: Java :: MCP Schemas"
extra["moduleName"] = "software.amazon.smithy.mcp.schemas"

dependencies {
    api(project(":core"))
    api(libs.smithy.model)
    api(project(":server:server-api"))
    smithyBuild(project(":codegen:plugins:types-codegen"))
    smithyBuild(project(":codegen:plugins:server-codegen"))
}

sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}

afterEvaluate {
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")
    sourceSets {
        main {
            java {
                srcDir(typePath)
                srcDir(serverPath)
                include("software/**")
            }
            resources {
                srcDir(typePath)
                srcDir(serverPath)
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
    mustRunAfter(tasks.compileJava)
}

tasks.processResources {
    dependsOn(tasks.compileJava)
}
