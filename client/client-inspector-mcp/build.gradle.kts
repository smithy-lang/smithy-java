plugins {
    id("smithy-java.module-conventions")
    application
}

description = "Dev/eval-only MCP control plane for inspecting and controlling a smithy-java client at runtime"

extra["displayName"] = "Smithy :: Java :: Client :: Inspector MCP"
extra["moduleName"] = "software.amazon.smithy.java.client.inspector.mcp"

dependencies {
    api(project(":core"))
    api(project(":client:client-core"))
    api(project(":http:http-api"))

    implementation(project(":context"))
    implementation(project(":logging"))
    implementation(project(":server:server-api"))
    implementation(project(":mcp:mcp-server"))
    implementation(project(":client:dynamic-client"))
    implementation(project(":dynamic-schemas"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))

    // The Smithy model for the control service is assembled at runtime.
    implementation(libs.smithy.model)

    // The contrived workload drives a real REST-JSON client against a mock backend so an agent
    // has real traffic to inspect. These back both the WorkloadService and the demo launcher.
    implementation(project(":client:client-mock-plugin"))
    implementation(project(":aws:client:aws-client-restjson"))
    implementation(project(":aws:client:aws-client-core"))
    implementation(project(":aws:aws-sigv4"))
    implementation(project(":aws:sdkv2:aws-sdkv2-auth"))
    implementation(libs.aws.sdk.auth)
    // The SDK's real retry strategy, so the workload retries a 429 for the real reason.
    implementation(project(":retries"))
    // AWS protocol/auth trait definitions used by the workload model (restJson1, sigv4).
    implementation(libs.smithy.aws.traits)

    // Route the SDK's SLF4J logging into java.util.logging so the InspectorLogHandler can capture it.
    runtimeOnly("org.slf4j:slf4j-jdk14:1.7.36")

    // The protocol-level test speaks real MCP JSON-RPC using the wire models + JSON codec.
    testImplementation(project(":mcp:mcp-schemas"))
}

// Run the demo server over stdio via the application plugin's generated start scripts. Each
// dependency jar stays separate on the classpath, so every dependency's META-INF/smithy/manifest
// survives and runtime model discovery (including the AWS protocol/auth traits) works — which a
// merged fat jar would collapse into a single manifest.
application {
    mainClass.set("software.amazon.smithy.java.client.inspector.mcp.demo.InspectorDemoServer")
}
