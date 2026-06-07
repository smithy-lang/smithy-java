import java.io.Serializable

plugins {
    id("smithy-java.codegen-plugin-conventions")
    id("smithy-java.publishing-conventions")
    id("smithy-java.jmh-conventions")
}

description = "Smithy Java code generation plugin"

extra["displayName"] = "Smithy :: Java :: Codegen :: Plugin"
extra["moduleName"] = "software.amazon.smithy.java.codegen.plugin"

dependencies {
    api(project(":codegen:codegen-core"))
    implementation(project(":framework-errors"))
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

// Ensure generate tasks that use it source set resources depend on base generateSources
listOf("generateSourcesClient", "generateSourcesServer", "generateSourcesTypes").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn("generateSources")
    }
}

tasks.test {
    failOnNoDiscoveredTests = false
}

configureIntegTests {
    awsModelTests = true
}

val artifactStatsDir = project.layout.buildDirectory.dir("reports/artifact-stats")

class PrintArtifactSizeStats(
    private val statsPath: String,
) : Action<Task>,
    Serializable {
    override fun execute(task: Task) {
        val statsFile = File(statsPath, "artifact-size-stats.tsv")
        if (!statsFile.exists()) {
            return
        }

        val entries =
            statsFile
                .readLines()
                .filter { it.isNotBlank() }
                .map { line ->
                    val (name, size) = line.split('\t')
                    name to size.toLong()
                }.sortedBy { it.second }

        if (entries.isEmpty()) {
            return
        }

        val total = entries.sumOf { it.second }
        val avg = total / entries.size
        val (minName, minSize) = entries.first()
        val (maxName, maxSize) = entries.last()

        fun humanReadable(bytes: Long): String =
            when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
                else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            }

        println()
        println("========== Compiled Artifact Size Statistics ==========")
        println("  SDKs processed: ${entries.size}")
        println("  Average size:   ${humanReadable(avg)} (%,d bytes)".format(avg))
        println("  Min size:       ${humanReadable(minSize)} (%,d bytes) -> $minName".format(minSize))
        println("  Max size:       ${humanReadable(maxSize)} (%,d bytes) -> $maxName".format(maxSize))
        println("  Total:          ${humanReadable(total)} (%,d bytes)".format(total))
        println("=======================================================")
        println()
        println("Top 5 largest SDKs:")
        for ((name, size) in entries.takeLast(5).reversed()) {
            println("  %-50s %s".format(name, humanReadable(size)))
        }
    }
}

tasks.named<Test>("integ") {
    val statsPath = artifactStatsDir.get().asFile.absolutePath
    systemProperty("artifactStatsDir", statsPath)
    outputs.dir(artifactStatsDir)
    doLast(PrintArtifactSizeStats(statsPath))
}
