plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.integ-test-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    smithyBuild(project(":codegen:plugins"))
}

// Add generated Java sources to the main sourceset
afterEvaluate {
    val clientPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-client-codegen")
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")
    val typesPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    sourceSets {
        main {
            java {
                srcDir(clientPath)
                srcDir(serverPath)
                srcDir(typesPath)
            }
            resources {
                srcDir(typesPath)
            }
        }
    }
}

tasks {
    val smithyBuild by getting
    compileJava {
        dependsOn(smithyBuild)
    }
    processResources {
        dependsOn(smithyBuild)
    }
}

// Helps Intellij plugin identify models
sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}
