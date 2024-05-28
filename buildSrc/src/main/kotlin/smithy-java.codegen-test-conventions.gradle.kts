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
    var clientOutputPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-client-codegen")
    sourceSets {
        main {
            java {
                srcDir(clientOutputPath)
            }
        }
    }
}

// Ignore generated generated code for formatter check
spotless {
    java {
        targetExclude("**/build/**/*.*")
    }
}


tasks.named("compileJava") {
    dependsOn("smithyBuild")
}
