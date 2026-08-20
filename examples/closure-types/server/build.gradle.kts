/**
 * Combined mode: the service generates as a server, and the event types from the
 * closure generate alongside it. The service publishes those events, so it needs them
 * even though no operation refers to them.
 */

plugins {
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    val smithyJavaVersion: String by project
    val smithyVersion: String by project

    smithyBuild("software.amazon.smithy.java:codegen-plugin:$smithyJavaVersion")
    implementation("software.amazon.smithy:smithy-protocol-traits:$smithyVersion")

    // Server mode needs server-api on the codegen classpath and at runtime.
    smithyBuild("software.amazon.smithy.java:server-api:$smithyJavaVersion")
    api("software.amazon.smithy.java:server-api:$smithyJavaVersion")

    implementation("software.amazon.smithy.java:server-rpcv2-cbor:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:cbor-codec:$smithyJavaVersion")

    // Publishes events to an SNS topic.
    implementation("software.amazon.awssdk:sns:2.47.6")
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
