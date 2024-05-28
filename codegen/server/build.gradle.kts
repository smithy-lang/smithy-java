plugins {
    id("smithy-java.codegen-plugin-conventions")
    id("smithy-java.integ-test-conventions")
}

description = "This module provides the codegen plugin for Smithy java server codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Server"
extra["moduleName"] = "software.amazon.smithy.java.codegen.server"

dependencies {
    implementation(project(":server-core"))
    implementation(project(":core"))
    implementation(project(":codegen:client"))
    implementation(libs.smithy.codegen)
    api(libs.smithy.aws.traits)
}

// Execute building of Java classes using an executable class
// These classes will then be used by integration tests and benchmarks
val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()
val generateSrcTask = tasks.register<JavaExec>("generateSources") {
    delete(files(generatedSrcDir))
    dependsOn("test")
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + sourceSets["it"].resources.getSourceDirectories()
    mainClass = "software.amazon.smithy.java.codegen.server.TestServerJavaCodegenRunner"
    environment("service", "smithy.java.codegen.server.test#TestService")
    environment("namespace", "smithy.java.codegen.server.test")
    environment("output", generatedSrcDir)
}

// Add generated POJOs to integ tests and jmh benchmark
sourceSets {
    it {
        java {
            srcDir(generatedSrcDir)
        }
    }
}

tasks {
    integ {
        dependsOn(generateSrcTask)
    }
    compileItJava {
        dependsOn(generateSrcTask)
    }
    spotbugsIt {
        enabled = false
    }
}

// Ignore generated generated code for formatter check
spotless {
    java {
        targetExclude("**/build/**/*.*")
    }
}
