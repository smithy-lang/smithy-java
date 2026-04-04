plugins {
    id("smithy-java.codegen-plugin-conventions")
    id("smithy-java.publishing-conventions")
    alias(libs.plugins.jmh)
}

description = "Smithy Java code generation plugin"

extra["displayName"] = "Smithy :: Java :: Codegen :: Plugin"
extra["moduleName"] = "software.amazon.smithy.java.codegen.plugin"

dependencies {
    api(project(":codegen:codegen-core"))
    compileOnly(project(":client:client-core"))
    compileOnly(project(":client:client-rulesengine"))
    compileOnly(project(":client:client-waiters"))
    compileOnly(project(":server:server-api"))
    // Test deps (needed to compile and run generated code in tests)
    testImplementation(project(":client:client-core"))
    testImplementation(project(":client:client-rulesengine"))
    testImplementation(project(":client:client-waiters"))
    testImplementation(project(":server:server-api"))
    testImplementation(project(":server:server-core"))
    testImplementation(project(":aws:client:aws-client-restjson"))
    testImplementation(libs.smithy.aws.traits)
    testImplementation(libs.smithy.rules)
    testImplementation(libs.jspecify)

    // Integration test deps
    itImplementation(project(":client:client-core"))
    itImplementation(project(":client:client-rulesengine"))
    itImplementation(project(":server:server-core"))
    itImplementation(project(":server:server-api"))
    itImplementation(project(":client:client-waiters"))
    itImplementation(project(":aws:client:aws-client-restjson"))
    itImplementation(project(":codecs:json-codec", configuration = "shadow"))
    itImplementation(libs.smithy.aws.traits)
    itImplementation(libs.smithy.rules)

    // Additional deps for AWS model codegen compilation test
    itImplementation(project(":aws:aws-sigv4"))
    itImplementation(project(":aws:aws-auth-api"))
    itImplementation(project(":aws:client:aws-client-core"))
    itImplementation(project(":aws:client:aws-client-http"))
    itImplementation(project(":aws:client:aws-client-rulesengine"))
    itImplementation(libs.smithy.aws.endpoints)

    // JMH benchmark deps (generated code and its runtime deps)
    jmhImplementation(sourceSets["it"].output)
    jmhImplementation(project(":client:client-core"))
    jmhImplementation(project(":server:server-core"))
    jmhImplementation(project(":server:server-api"))
    jmhImplementation(project(":aws:client:aws-client-restjson"))
}

// Core codegen test runner
addGenerateSrcsTask("software.amazon.smithy.java.codegen.utils.TestJavaCodegenRunner")
// Client codegen test runner
addGenerateSrcsTask("software.amazon.smithy.java.codegen.client.TestServerJavaClientCodegenRunner", "Client", null)
// Server codegen test runner
addGenerateSrcsTask("software.amazon.smithy.java.codegen.server.TestServerJavaCodegenRunner", "Server", null)
// Types codegen test runner
addGenerateSrcsTask("software.amazon.smithy.java.codegen.types.TestJavaTypeCodegenRunner", "Types", null)

sourceSets {
    it {
        // Add test plugin to classpath
        compileClasspath += sourceSets["test"].output
    }
}

// Wire JMH source set to see generated integration test classes
sourceSets.named("jmh") {
    compileClasspath += sourceSets["it"].output + sourceSets["it"].compileClasspath
    runtimeClasspath += sourceSets["it"].output + sourceSets["it"].runtimeClasspath
}

tasks.named("compileJmhJava") {
    dependsOn("compileItJava")
}

jmh {}

// Ensure generate tasks that use it source set resources depend on base generateSources
listOf("generateSourcesClient", "generateSourcesServer", "generateSourcesTypes").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn("generateSources")
    }
}

tasks.test {
    failOnNoDiscoveredTests = false
}

// If API_MODELS_AWS_DIR is set, add it as a task input so Gradle doesn't cache results.
System.getenv("API_MODELS_AWS_DIR")?.let { modelsDir ->
    val dir = file(modelsDir)
    if (dir.isDirectory) {
        tasks.named<Test>("integ") {
            inputs.dir(dir).withPathSensitivity(PathSensitivity.RELATIVE)
            maxHeapSize = "4g"
        }
    }
}
