import org.gradle.api.artifacts.component.ModuleComponentIdentifier

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
// Combined client + types codegen test runner
addGenerateSrcsTask("software.amazon.smithy.java.codegen.combined.TestCombinedModeCodegenRunner", "Combined", null)

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
listOf("generateSourcesClient", "generateSourcesServer", "generateSourcesTypes", "generateSourcesCombined")
    .forEach { taskName ->
        tasks.named(taskName) {
            dependsOn("generateSources")
        }
    }

tasks.test {
    failOnNoDiscoveredTests = false
}

configureIntegTests {
    awsModelTests = true
    awsModelsOnTestClasspath = false
}

/*
 * codegenSize writes build/codegen-size/report.json. Model resolution requires -PawsModelsTests.
 *
 *   ./gradlew :codegen:codegen-plugin:codegenSize -PawsModelsTests
 *       [-PcodegenSizeOnly=ec2,s3] [-PcodegenSizeBaseline=<base report.json>]
 *       [-PcodegenSizeArgs="--modes client --dump codegen/codegen-plugin/build/codegen-size/dump"]
 *   ./gradlew :codegen:codegen-plugin:codegenSizeCompare -PcodegenSizeHead=<report.json> [-PcodegenSizeBase=<report.json>]
 *
 * codegenSizeCompare writes report.md and status (unchanged, changed, or regression). It fails on new incompatibilities.
 * Without -PcodegenSizeHead, it uses the codegenSize report from the same build.
 */
val codegenSizeDir = layout.buildDirectory.dir("codegen-size")
val codegenSizeReport = codegenSizeDir.map { it.file("report.json") }
val codegenSizeMarkdown = codegenSizeDir.map { it.file("report.md") }
val codegenSizeStatus = codegenSizeDir.map { it.file("status") }
val codegenSizeMainClass = "software.amazon.smithy.java.codegen.size.CodegenSize"
val awsModelsEnabled = providers.gradleProperty("awsModelsTests").isPresent

fun codegenSizeProperty(name: String): String? =
    providers.gradleProperty(name).orNull?.let { rootDir.resolve(it).absolutePath }

val codegenSizeModels by tasks.registering(Sync::class) {
    description = "Collects the AWS API model jars pinned by the aws-api-models BOM (requires -PawsModelsTests)"
    from(
        configurations["awsModels"].incoming.artifactView {
            componentFilter { it is ModuleComponentIdentifier && it.group == "software.amazon.api.models" }
        }.files,
    )
    into(codegenSizeDir.map { it.dir("models") })
}

val codegenSize by tasks.registering(JavaExec::class) {
    description = "Generates, compiles, and measures code for the AWS API models (requires -PawsModelsTests)"
    group = "verification"
    classpath = sourceSets["it"].runtimeClasspath
    mainClass = codegenSizeMainClass
    maxHeapSize = "6g"
    workingDir = rootDir
    inputs.files(codegenSizeModels)
    outputs.file(codegenSizeReport)
    // Capture locally to avoid a build-script reference that prevents configuration caching.
    val enabled = awsModelsEnabled
    doFirst {
        if (!enabled) {
            throw GradleException("Run with -PawsModelsTests to resolve the AWS API models")
        }
    }
    val baseline = codegenSizeProperty("codegenSizeBaseline")
    args =
        buildList {
            add("report")
            add("--models")
            add(codegenSizeModels.get().destinationDir.absolutePath)
            add("--out")
            add(codegenSizeReport.get().asFile.absolutePath)
            add("--bom")
            add(libs.versions.aws.api.models.get())
            providers.gradleProperty("codegenSizeOnly").orNull?.let {
                add("--only")
                add(it)
            }
            if (baseline != null) {
                add("--baseline")
                add(baseline)
                add("--markdown")
                add(codegenSizeMarkdown.get().asFile.absolutePath)
                add("--status")
                add(codegenSizeStatus.get().asFile.absolutePath)
            }
            providers.gradleProperty("codegenSizeArgs").orNull?.let { addAll(it.trim().split(Regex("\\s+"))) }
        }
    inputs.property("args", args)
}

val codegenSizeCompare by tasks.registering(JavaExec::class) {
    description = "Writes a Markdown comparison of two codegenSize reports and fails on new incompatibilities"
    group = "verification"
    classpath = sourceSets["it"].runtimeClasspath
    mainClass = codegenSizeMainClass
    val base = codegenSizeProperty("codegenSizeBase")
    val head = codegenSizeProperty("codegenSizeHead")
    if (head == null) {
        dependsOn(codegenSize)
    }
    args =
        buildList {
            add("compare")
            if (base != null) {
                add(base)
            }
            add(head ?: codegenSizeReport.get().asFile.absolutePath)
            add("--markdown")
            add(codegenSizeMarkdown.get().asFile.absolutePath)
            add("--status")
            add(codegenSizeStatus.get().asFile.absolutePath)
        }
    outputs.files(codegenSizeMarkdown, codegenSizeStatus)
    outputs.upToDateWhen { false }
}
