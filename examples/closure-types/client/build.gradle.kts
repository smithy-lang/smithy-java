/**
 * Generates a BirdWatcher client and nothing else. There is no `closure` setting, so
 * generation is driven by the service in the usual way. A client calls the service and
 * has no reason to know about the events it publishes.
 */

plugins {
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    val smithyJavaVersion: String by project
    val smithyVersion: String by project

    smithyBuild("software.amazon.smithy.java:codegen-plugin:$smithyJavaVersion")
    implementation("software.amazon.smithy:smithy-protocol-traits:$smithyVersion")

    // Client mode needs client-core on the codegen classpath and at runtime.
    smithyBuild("software.amazon.smithy.java:client-core:$smithyJavaVersion")
    api("software.amazon.smithy.java:client-core:$smithyJavaVersion")

    implementation("software.amazon.smithy.java:client-rpcv2-cbor:$smithyJavaVersion")
}

// Compile the generated sources and package the generated resources, which hold the
// service file used to discover the schemas at runtime.
afterEvaluate {
    val generated = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets["main"].java.srcDir("$generated/java")
    sourceSets["main"].resources.srcDir("$generated/resources")
}

tasks.compileJava { dependsOn(tasks.named("smithyBuild")) }
tasks.processResources { dependsOn(tasks.named("smithyBuild")) }
