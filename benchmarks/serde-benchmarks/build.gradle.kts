plugins {
    id("smithy-java.java-conventions")
    id("com.gradleup.shadow")
    id("smithy-java.jmh-conventions")
    id("software.amazon.smithy.java.gradle.smithy-java")
}

description = "Serde (serialization/deserialization) microbenchmarks for smithy-java codecs."

smithyJava {
    projections.addAll(
        "aws-json-rpc-1-0-client",
        "aws-query-client",
        "rest-json-client",
        "rest-xml-client",
        "rpc-v2-cbor-client",
    )
}

// Benchmarks intentionally target JDK 25 (the rest of smithy-java targets 21).
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

dependencies {
    implementation(libs.smithy.model)
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.protocol.traits)
    implementation(libs.smithy.protocol.test.traits)
    implementation(libs.smithy.utils)

    jmh(project(":io"))
    jmh(project(":logging"))
    jmh(project(":codecs:json-codec", configuration = "shadow"))
    jmh(project(":codecs:cbor-codec"))
    jmh(project(":codecs:xml-codec"))

    jmh(project(":client:client-core"))
    jmh(project(":client:client-http"))
    jmh(project(":client:client-http-binding"))
    jmh(project(":client:client-rpcv2-cbor"))
    jmh(project(":aws:client:aws-client-awsjson"))
    jmh(project(":aws:client:aws-client-awsquery"))
    jmh(project(":aws:client:aws-client-restjson"))
    jmh(project(":aws:client:aws-client-restxml"))

    jmh(project(":protocol-test-harness"))
}

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
    description = "Copy benchmark .smithy files to META-INF/smithy/ and generate the model manifest."
    sourceDir.set(layout.projectDirectory.dir("model"))
    outputDir.set(layout.buildDirectory.dir("generated-resources/smithy-manifest"))
}

sourceSets.named("jmh") {
    resources {
        srcDir(generateSmithyManifest)
    }
}

tasks.named("processJmhResources") {
    dependsOn(generateSmithyManifest)
}

// Test case:  -Pjmh.testCaseId=rpcv2Cbor_PutItemRequest_BinaryData_S
val fast = providers.gradleProperty("jmh.fast").isPresent
jmh {
    benchmarkMode.set(listOf("sample"))
    if (!fast) {
        warmupIterations = 5
        iterations = 10
    }
    timeOnIteration = "5s"
    jvmArgs.addAll(
        "-Xms1g",
        "-Xmx1g",
        "-XX:+UseG1GC",
        "-XX:+AlwaysPreTouch",
        "-Dsmithy-java.json-provider=smithy",
        "-Dsmithy-java.xml-provider=smithy",
    )
    providers.gradleProperty("jmh.testCaseId").orNull?.let { id ->
        val prop = objects.listProperty(String::class.java)
        prop.add(id)
        benchmarkParameters.put("testCaseId", prop)
    }
    resultFormat = "json"
    resultsFile = layout.buildDirectory.file("results/jmh/results.json")
}

tasks.jmhJar {
    mergeServiceFiles()
    append("META-INF/smithy/manifest")
}

tasks.register<JavaExec>("convertJmhResults") {
    group = "benchmarks"
    description = "Convert JMH JSON output to the cross-language serde benchmark schema."

    mainClass.set("software.amazon.smithy.java.benchmarks.serde.JmhResultConverter")
    classpath = sourceSets["jmh"].runtimeClasspath

    val inputFile = layout.buildDirectory.file("results/jmh/results.json")
    val defaultPrefix = layout.buildDirectory.file("results/jmh/output").map { it.asFile.absolutePath }
    val outputPrefix = providers.gradleProperty("outputPrefix").orElse(defaultPrefix)

    inputs.file(inputFile)
    outputs.files(
        outputPrefix.map { file("$it.json") },
        outputPrefix.map { file("$it.md") },
    )

    args(
        "--input",
        inputFile.get().asFile.absolutePath,
        "--output-prefix",
        outputPrefix.get(),
    )
}

tasks.named("jmh") {
    finalizedBy("convertJmhResults")
}
