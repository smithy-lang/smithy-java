// Note: Not published.
//
// Feb-2026 baseline build of the cross-SDK serde E2E ops/CPU-sec harness: the current harness built against
// the SDK as of the baseline commit, so old SDK behaviour is measured with the current test infrastructure.
// Differences from the current module, all forced by the older SDK:
//   - the smithy-java HTTP client and BoringSSL do not exist yet, so the https mode drives the JDK HTTP client
//     (JavaHttpClientTransport) and the fixture server's TLS comes from SSLServerSocket;
//   - the codegen plugin is `java-client-codegen` from `:codegen:plugins`, and there is no JMH convention.
plugins {
    id("smithy-java.java-conventions")
    alias(libs.plugins.shadow)
    application
    id("software.amazon.smithy.gradle.smithy-base")
}

description =
    "Cross-SDK serde E2E ops/CPU-sec benchmark (Feb-2026 baseline): generated-client calls against an in-process stub or a fixture server over HTTPS."

application {
    mainClass.set("software.amazon.smithy.java.benchmarks.e2e.E2eBenchmark")
    // Disable background JIT compilation for Gradle runs.
    applicationDefaultJvmArgs = listOf("-Xbatch")
}

val sharedModelDir = layout.projectDirectory.dir("../serde-benchmarks/model")

dependencies {
    implementation(project(":benchmarks:benchmark-commons"))

    // Codegen-time classpath. The baseline codegen plugin aggregator is :codegen:plugins; the client
    // builders wire in the protocol factories, the SigV4 auth-scheme factory and the credential-chain plugin.
    smithyBuild(project(":codegen:plugins"))
    smithyBuild(project(":client:client-core"))
    smithyBuild(project(":client:client-rpcv2-cbor"))
    smithyBuild(project(":aws:client:aws-client-awsjson"))
    smithyBuild(project(":aws:client:aws-client-awsquery"))
    smithyBuild(project(":aws:client:aws-client-restjson"))
    smithyBuild(project(":aws:client:aws-client-restxml"))
    smithyBuild(project(":aws:aws-sigv4"))
    smithyBuild(project(":aws:client:aws-client-core"))

    // The model is assembled at runtime (BenchmarkCases) to index the tagged test cases.
    implementation(libs.smithy.model)
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.protocol.traits)
    implementation(libs.smithy.protocol.test.traits)
    implementation(libs.smithy.utils)

    // Runtime stack under test: what a customer's generated client pulls in (baseline module set).
    implementation(project(":core"))
    implementation(project(":io"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(project(":client:client-core"))
    implementation(project(":client:client-http"))
    implementation(project(":client:client-http-binding"))
    implementation(project(":client:client-rpcv2-cbor"))
    implementation(project(":auth-api"))
    implementation(project(":retries-api"))
    implementation(project(":http:http-api"))
    implementation(project(":http:http-binding"))
    implementation(project(":aws:aws-sigv4"))
    implementation(project(":aws:aws-auth-api"))
    implementation(project(":aws:client:aws-client-core"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(project(":aws:client:aws-client-awsquery"))
    implementation(project(":aws:client:aws-client-restjson"))
    implementation(project(":aws:client:aws-client-restxml"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":codecs:cbor-codec"))
    implementation(project(":codecs:xml-codec"))

    // ProtocolTestDocument turns each test case's `params` Node into a typed input at setup.
    implementation(project(":protocol-test-harness"))
}

// Copies the shared .smithy files to META-INF/smithy/ and writes the model manifest so
// `Model.assembler().discoverModels()` finds them on the runtime classpath.
abstract class GenerateSmithyManifest : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val outRoot = outputDir.get().asFile
        val smithyDir = outRoot.resolve("META-INF/smithy")
        smithyDir.deleteRecursively()
        smithyDir.mkdirs()
        val srcRoot = sourceDir.get().asFile
        val entries = mutableListOf<String>()
        srcRoot.walkTopDown().filter { it.isFile && it.extension == "smithy" }.forEach { f ->
            val rel = f.relativeTo(srcRoot).invariantSeparatorsPath
            f.copyTo(smithyDir.resolve(rel), overwrite = true)
            entries += rel
        }
        entries.sort()
        smithyDir.resolve("manifest").writeText(entries.joinToString("\n", postfix = "\n"))
    }
}

val generateSmithyManifest by tasks.registering(GenerateSmithyManifest::class) {
    group = "build"
    description = "Copy the shared benchmark .smithy files to META-INF/smithy/ and generate the model manifest."
    sourceDir.set(sharedModelDir)
    outputDir.set(layout.buildDirectory.dir("generated-resources/smithy-manifest"))
}

abstract class WriteBuildInfo : DefaultTask() {
    @get:Input
    abstract val commit: Property<String>

    @get:Input
    abstract val branch: Property<String>

    @get:Input
    abstract val dirty: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val file = outputDir.get().asFile.resolve("META-INF/smithy-java-e2e-benchmarks/build-info.properties")
        file.parentFile.mkdirs()
        file.writeText("commit=${commit.get()}\nbranch=${branch.get()}\ndirty=${dirty.get()}\n")
    }
}

fun git(vararg args: String): Provider<String> =
    providers
        .exec {
            workingDir = rootDir
            commandLine("git", *args)
            isIgnoreExitValue = true
        }.standardOutput.asText
        .map { it.trim() }

val writeBuildInfo by tasks.registering(WriteBuildInfo::class) {
    group = "build"
    description = "Record the git commit and branch the benchmark jar was built from."
    commit.set(git("rev-parse", "HEAD").map { it.ifEmpty { "unknown" } })
    branch.set(git("rev-parse", "--abbrev-ref", "HEAD").map { it.ifEmpty { "unknown" } })
    dirty.set(git("status", "--porcelain", "--untracked-files=no").map { it.isNotEmpty() })
    outputDir.set(layout.buildDirectory.dir("generated-resources/build-info"))
}

// Each codegen projection registers its own META-INF/services/...SchemaIndex. Merge them into one
// descriptor per service type and feed that to processResources instead of the per-projection copies.
abstract class MergeServiceFiles : DefaultTask() {
    @get:InputFiles
    abstract val serviceDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile.resolve("META-INF/services")
        out.deleteRecursively()
        out.mkdirs()
        val merged = sortedMapOf<String, LinkedHashSet<String>>()
        serviceDirs.files.filter { it.isDirectory }.forEach { dir ->
            dir.listFiles()?.filter { it.isFile }?.forEach { file ->
                merged.getOrPut(file.name) { linkedSetOf() } +=
                    file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            }
        }
        merged.forEach { (name, providers) ->
            out.resolve(name).writeText(providers.joinToString("\n", postfix = "\n"))
        }
    }
}

// One codegen projection per protocol (awsQuery client support landed in this commit).
val codegenProjections =
    listOf(
        "aws-json-rpc-1-0-client",
        "aws-query-client",
        "rest-json-client",
        "rest-xml-client",
        "rpc-v2-cbor-client",
    )

afterEvaluate {
    val projectionPaths =
        codegenProjections.map { name ->
            smithy.getPluginProjectionPath(name, "java-client-codegen").get()
        }
    val mergeCodegenServiceFiles by tasks.registering(MergeServiceFiles::class) {
        group = "build"
        description = "Merge the META-INF/services descriptors emitted by the codegen projections."
        dependsOn("smithyBuild")
        // Baseline codegen output is flat: sources and META-INF sit directly under the projection path.
        projectionPaths.forEach { serviceDirs.from("$it/META-INF/services") }
        outputDir.set(layout.buildDirectory.dir("generated-resources/merged-services"))
    }
    sourceSets.named("main") {
        java {
            // Flat layout: the generated .java tree is directly under the projection path.
            projectionPaths.forEach { srcDir(it) }
        }
        resources {
            // The only generated resource is META-INF/services/SchemaIndex, merged above; the rest is
            // the model manifest and build info.
            srcDir(generateSmithyManifest)
            srcDir(writeBuildInfo)
        }
    }
    tasks.named<Copy>("processResources") {
        from(mergeCodegenServiceFiles)
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    dependsOn("smithyBuild")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("smithy-java-e2e-benchmark")
    archiveClassifier.set("")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesNotMatching(listOf("META-INF/services/**", "META-INF/smithy/manifest")) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    mergeServiceFiles()
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer::class.java) {
        resource = "META-INF/smithy/manifest"
    }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named("assemble") {
    dependsOn("shadowJar")
}
