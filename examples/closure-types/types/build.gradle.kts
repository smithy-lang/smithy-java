/**
 * Generates only the event types in the `com.example.audubon#events` closure. No
 * service is involved, so no `service` setting is needed.
 */

plugins {
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    val smithyJavaVersion: String by project
    val smithyVersion: String by project

    smithyBuild("software.amazon.smithy.java:codegen-plugin:$smithyJavaVersion")

    // Defines the @rpcv2Cbor trait on the service. The model needs it on its own
    // classpath to load.
    implementation("software.amazon.smithy:smithy-protocol-traits:$smithyVersion")

    api("software.amazon.smithy.java:core:$smithyJavaVersion")
    testImplementation("software.amazon.smithy.java:cbor-codec:$smithyJavaVersion")
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
