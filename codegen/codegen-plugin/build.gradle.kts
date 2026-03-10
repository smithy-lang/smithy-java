plugins {
    id("smithy-java.codegen-plugin-conventions")
    id("smithy-java.publishing-conventions")
}

description = "Smithy Java code generation plugin"

extra["displayName"] = "Smithy :: Java :: Codegen :: Plugin"
extra["moduleName"] = "software.amazon.smithy.java.codegen.plugin"

dependencies {
    api(libs.smithy.codegen)
    compileOnly(project(":client:client-api"))
    compileOnly(project(":client:client-rulesengine"))
    compileOnly(project(":client:client-waiters"))
    compileOnly(project(":server:server-api"))
    // Test deps (needed to compile and run generated code in tests)
    testImplementation(project(":client:client-api"))
    testImplementation(project(":client:client-core"))
    testImplementation(project(":client:client-rulesengine"))
    testImplementation(project(":client:client-waiters"))
    testImplementation(project(":server:server-api"))
    testImplementation(project(":server:server-core"))
    testImplementation(project(":aws:client:aws-client-restjson"))
    testImplementation(libs.smithy.aws.traits)
    testImplementation(libs.smithy.rules)
    testImplementation(project(":codegen:test-utils"))

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
        resources.srcDir("${layout.buildDirectory.get()}/generated-src/resources")
    }
}

tasks.named<ProcessResources>("processItResources") {
    dependsOn("generateSources", "generateSourcesClient")
    from("${layout.buildDirectory.get()}/generated-src-Client/resources")
}

// Ensure generate tasks that use it source set resources depend on base generateSources
listOf("generateSourcesClient", "generateSourcesServer", "generateSourcesTypes").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn("generateSources")
    }
}

tasks.test {
    failOnNoDiscoveredTests = false
}
