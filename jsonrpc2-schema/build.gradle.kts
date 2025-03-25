plugins {
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module provides the Smithy Java JSON-RPC 2.0 schema"
extra["displayName"] = "Smithy :: Java :: JsonRpc Schema"
extra["moduleName"] = "software.amazon.smithy.java.jsonrpc.model"

dependencies {
    smithyBuild(project(":codegen:plugins:types"))
    api(project(":core"))
    api(libs.smithy.model)
}

afterEvaluate {
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    sourceSets {
        main {
            java {
                srcDir(typePath)
            }
            resources {
                srcDir(typePath)
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}

tasks {
    compileJava {
        dependsOn("smithyBuild")
    }
    processResources {
        dependsOn(compileJava)
    }
    sourcesJar {
        dependsOn(smithyBuild)
    }
}
