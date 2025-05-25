import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import kotlin.jvm.java

plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.shadow)
    application
}

description =
    "MCP Server support"

extra["displayName"] = "Smithy :: Java :: MCP CLI"
extra["moduleName"] = "software.amazon.smithy.java.mcp.cli"

dependencies {
    implementation(project(":logging"))
    implementation(project(":mcp:mcp-server"))
    implementation(project(":server:server-proxy"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(libs.picocli)
    api(project(":mcp:mcp-cli-api"))
    api(project(":mcp:mcp-schemas"))
    implementation(libs.smithy.utils)

    // TODO these need to be dynamically loaded
    implementation(project(":aws:aws-mcp-cli-commands"))
    implementation(project(":aws:aws-service-bundle"))
    implementation(project(":aws:client:aws-client-restjson"))
    implementation(project(":aws:client:aws-client-awsjson"))
}

application {
    mainClass = "software.amazon.smithy.java.mcp.cli.McpCli"
    applicationDefaultJvmArgs = listOf("-Dorg.slf4j.simpleLogger.defaultLogLevel=off")
    applicationName = "smithy-mcp"
}

val generateVersionFile =
    tasks.register("generateVersionFile") {
        val versionFile =
            sourceSets.main
                .get()
                .output.resourcesDir
                ?.resolve("software/amazon/smithy/java/mcp/cli/VERSION")!!

        outputs.file(versionFile)
        val projectVersion = project.version.toString()

        doLast {
            versionFile.writeText(projectVersion)
        }
    }

tasks.processResources {
    dependsOn(generateVersionFile)
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.shadowJar {
    mergeServiceFiles()
    transform(AppendingTransformer::class.java) {
        resource = "META-INF/smithy/manifest"
    }
}
