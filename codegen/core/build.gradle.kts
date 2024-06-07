plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the core codegen functionality for Smithy java"

extra["displayName"] = "Smithy :: Java :: Codegen :: Core"
extra["moduleName"] = "software.amazon.smithy.java.codegen"

dependencies {
    itImplementation(project(":json-codec"))
}

// Execute building of Java classes using an executable class
// These classes will then be used by integration tests and benchmarks
val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()
tasks.register<JavaExec>("generateSources") {
    delete(generatedSrcDir)
    dependsOn("test")
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + sourceSets["it"].resources.sourceDirectories
    mainClass = "software.amazon.smithy.java.codegen.utils.TestJavaCodegenRunner"
    environment("service", "smithy.java.codegen.test#TestService")
    environment("namespace", "io.smithy.codegen.test")
    environment("output", generatedSrcDir)
}

tasks {
    test {
        finalizedBy("integ")
    }
    integ {
        dependsOn("generateSources")
    }
    compileItJava {
        dependsOn("generateSources")
    }
}
