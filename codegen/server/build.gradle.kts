plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy java server codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Server"
extra["moduleName"] = "software.amazon.smithy.java.codegen.server"

dependencies {
    implementation(project(":server-core"))
}

// Execute building of Java classes using an executable class
// These classes will then be used by integration tests and benchmarks
val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()
val generateSrcTask = tasks.register<JavaExec>("generateSources") {
    delete(generatedSrcDir)
    dependsOn("test")
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + sourceSets["it"].resources.sourceDirectories
    mainClass = "software.amazon.smithy.java.codegen.server.TestServerJavaCodegenRunner"
    environment("service", "smithy.java.codegen.server.test#TestService")
    environment("namespace", "smithy.java.codegen.server.test")
    environment("output", generatedSrcDir)
}

tasks {
    test {
        finalizedBy("integ")
    }
    integ {
        dependsOn(generateSrcTask)
    }
    compileItJava {
        dependsOn(generateSrcTask)
    }
}
