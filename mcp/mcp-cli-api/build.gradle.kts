plugins {
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module provides a apis for MCP CLI"

extra["displayName"] = "Smithy :: Java :: MCP CLI API"
extra["moduleName"] = "software.amazon.smithy.mcp.cli.api"

dependencies {
    api(project(":core"))
    api(libs.smithy.model)
    api(libs.picocli)
    annotationProcessor(libs.picocli.codegen)
    api(project(":mcp:mcp-bundle-api"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":logging"))
    smithyBuild(project(":codegen:plugins:types-codegen"))
}

tasks.compileJava {
    options.compilerArgs.add("-Aproject=mcp-cli-api")
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

tasks.withType<JavaCompile> {
    options.release.set(21)
}

// Needed because sources-jar needs to run after smithy-build is done
tasks.sourcesJar {
    mustRunAfter(tasks.compileJava)
}

tasks.processResources {
    dependsOn(tasks.compileJava)
}
