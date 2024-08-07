plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy java type codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Types"
extra["moduleName"] = "software.amazon.smithy.java.codegen.types"

// Execute building of Java classes using an executable class
// These classes will then be used by integration tests and benchmarks
val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()
val generateSrcTask = tasks.register<JavaExec>("generateSources") {
    delete(generatedSrcDir)
    dependsOn("test")
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + sourceSets["it"].resources.sourceDirectories
    mainClass = "software.amazon.smithy.java.codegen.types.TestTypesJavaCodegenRunner"
    environment("namespace", "smithy.java.codegen.types.test")
    environment("output", generatedSrcDir)
}

tasks {
    integ {
        dependsOn(generateSrcTask)
    }
    test {
        finalizedBy(integ)
    }
    compileItJava {
        dependsOn(generateSrcTask)
    }
}
