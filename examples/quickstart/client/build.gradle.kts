description = "A package used for executing test of a cafe service client"

plugins {
    `java-library`
    // Executes smithy-build to generate client code
    alias(libs.plugins.smithy.base)
}

dependencies {
    smithyBuild(libs.smithy.client.codegen)
    implementation(project(":lib"))
    api(project(":plugins"))
    implementation(libs.smithy.java.client.restjson)
}

// Add generated Java sources to the main sourceSet
tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

afterEvaluate {
    val clientPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-client-codegen")
    sourceSets {
        main {
            java {
                srcDir(clientPath)
            }
        }
    }
}

// Set up integration testing task. This is set up separate from the `test` task to
// avoid automatically running tests as part of build.
sourceSets {
    val main by getting
    val test by getting
    create("it") {
        compileClasspath += main.output + configurations["testRuntimeClasspath"] + configurations["testCompileClasspath"]
        runtimeClasspath += output + compileClasspath + test.runtimeClasspath + test.output
    }
}

// Add the integ test task
tasks.register<Test>("integ") {
    useJUnitPlatform()
    testClassesDirs = sourceSets["it"].output.classesDirs
    classpath = sourceSets["it"].runtimeClasspath
}

// Junit test dependencies
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
}
tasks.withType<Test> {
    useJUnitPlatform()
}

