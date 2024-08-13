plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy java client codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Client"
extra["moduleName"] = "software.amazon.smithy.java.codegen.client"

dependencies {
    implementation(project(":client-core"))

    testImplementation(project(":client-aws-rest-json1"))
    testImplementation(libs.smithy.aws.traits)

    itImplementation(project(":client-aws-rest-json1"))
}

// Execute building of Java classes using an executable class
// These classes will then be used by integration tests and benchmarks
val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()
val generateSrcTask = tasks.register<JavaExec>("generateSources") {
    delete(files(generatedSrcDir))
    dependsOn("test")
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + sourceSets["it"].resources.sourceDirectories
    mainClass = "software.amazon.smithy.java.codegen.client.TestServerJavaClientCodegenRunner"
    environment("service", "smithy.java.codegen.server.test#TestService")
    environment("namespace", "smithy.java.codegen.server.test")
    environment("output", generatedSrcDir)
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
