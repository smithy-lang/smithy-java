plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.integ-test-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    smithyBuild(project(":codegen:client"))
    smithyBuild(project(":codegen:server"))
}

// Add generated Java sources to the main sourceset
afterEvaluate {
    val clientPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-client-codegen")
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")
    sourceSets {
        main {
            java {
                srcDir(clientPath)
                srcDir(serverPath)
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

// Helps Intellij plugin identify models
sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}
