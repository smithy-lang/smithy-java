plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.integ-test-conventions")
}

description = "This module provides the core codegen functionality for Smithy java"
group = "software.amazon.smithy.java.codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Core"
extra["moduleName"] = "software.amazon.smithy.java.codegen"

dependencies {
    implementation(libs.smithy.codegen)
    implementation(project(":core"))
    itImplementation(project(":json-codec"))
}

// Execute building of Java classes using an executable class
// These classes will then be used by integration tests and benchmarks
val generatedPojoDir = "${layout.buildDirectory.get()}/generated-pojos"
tasks.register<JavaExec>("generatePojos") {
    dependsOn("test")
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + sourceSets["it"].resources.getSourceDirectories()
    mainClass = "software.amazon.smithy.java.codegen.utils.TestJavaCodegenRunner"
    environment("service", "smithy.java.codegen.test#TestService")
    environment("namespace", "io.smithy.codegen.test")
    environment("output", generatedPojoDir)
}

// Add generated POJOs to integ tests and jmh benchmark
sourceSets {
    it {
        java {
            srcDir(generatedPojoDir)
        }
    }
}

tasks {
    integ {
        dependsOn("generatePojos")
    }
    compileItJava {
        dependsOn("generatePojos")
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
