plugins {
    id("smithy-java.module-conventions")
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
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.release.set(21)
    }
}

spotbugs {
    ignoreFailures = true
}
