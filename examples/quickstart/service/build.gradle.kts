description = "A package defining a cafe service implementation"

plugins {
    `java-library`
    application
    // Executes smithy-build to generate server stubs
    alias(libs.plugins.smithy.base)
}

dependencies {
    // Server code generator
    smithyBuild(libs.smithy.codegen.plugins)
    implementation(project(":lib"))

    // Server dependencies
    api(libs.server.core)
    implementation(libs.server.netty)
    api(libs.server.aws.rest.json1)
}

// Add generated Java code to the main source set
tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

afterEvaluate {
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")
    sourceSets {
        main {
            java {
                srcDir(serverPath)
            }
        }
    }
}

// Use that application plugin to start the service via the `run` task.
application {
    mainClass = "software.amazon.smithy.java.server.example.CafeService"
}
