plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.codegen-plugin-conventions")
    `java-test-fixtures`
}

description =
    "MCP Server support"

extra["displayName"] = "Smithy :: Java :: MCP Server"
extra["moduleName"] = "software.amazon.smithy.java.server.mcp"

dependencies {
    api(project(":server:server-api"))
    implementation(project(":server:server-core"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":mcp:mcp-schemas"))
    implementation(project(":smithy-ai-traits"))
    implementation(project(":client:client-core"))
    implementation(project(":client:client-http"))
    testRuntimeOnly(libs.smithy.aws.traits)
    testRuntimeOnly(project(":aws:client:aws-client-awsjson"))
    testImplementation(project(":server:server-proxy"))

    testImplementation(project(":codegen:codegen-plugin"))
    testImplementation(libs.json.schema.validator)
}

spotbugs {
    ignoreFailures = true
}

addGenerateSrcsTask("software.amazon.smithy.java.mcp.server.utils.TestJavaCodegenRunner", null, null, "server")

tasks.named<Test>("integ") {
    useJUnitPlatform {
        excludeTags("conformance")
    }
}

tasks.register<Test>("conformance") {
    description = "Runs the official Model Context Protocol conformance scenarios"
    group = "verification"
    useJUnitPlatform {
        includeTags("conformance")
    }
    testClassesDirs = sourceSets["it"].output.classesDirs
    classpath = sourceSets["it"].runtimeClasspath
    shouldRunAfter("integ")
}
