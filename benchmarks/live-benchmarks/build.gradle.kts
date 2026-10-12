plugins {
    id("smithy-java.java-conventions")
    id("com.gradleup.shadow")
    application
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "Live AWS service benchmarks (DynamoDB GetItem/PutItem latency, S3 GetObject/PutObject throughput). Not the cross-SDK e2e suite; see e2e-benchmarks."

application {
    mainClass.set("software.amazon.smithy.java.benchmarks.live.WorkloadRunner")
}

dependencies {
    implementation(project(":benchmarks:benchmark-commons"))

    smithyBuild(project(":codegen:codegen-plugin"))
    smithyBuild(project(":client:client-core"))
    smithyBuild(project(":client:client-waiters"))
    // Codegen loads AwsRulesExtension through ServiceLoader.
    smithyBuild(project(":aws:client:aws-client-rulesengine"))
    // Codegen loads the S3 Express plugin through reflection.
    smithyBuild(project(":aws:aws-sigv4-s3express"))

    // The source projection loads these models from runtimeClasspath.
    implementation("software.amazon.api.models:dynamodb:1.0.14")
    implementation("software.amazon.api.models:s3:1.0.24")

    implementation(project(":core"))
    implementation(project(":io"))
    implementation(project(":logging"))
    implementation(project(":context"))
    implementation(project(":client:client-core"))
    implementation(project(":client:client-http"))
    implementation(project(":client:client-http-binding"))
    implementation(project(":client:client-rulesengine"))
    implementation(project(":client:client-waiters"))
    implementation(project(":rulesengine"))
    implementation(project(":endpoints"))
    implementation(project(":auth-api"))
    implementation(project(":retries-api"))
    implementation(project(":retries"))
    implementation(project(":http:http-api"))
    implementation(project(":http:http-binding"))

    implementation(project(":aws:aws-sigv4"))
    implementation(project(":aws:aws-sigv4-s3express"))
    implementation(project(":aws:aws-auth-api"))
    implementation(project(":aws:client:aws-client-core"))
    implementation(project(":aws:client:aws-client-http"))
    implementation(project(":aws:client:aws-client-restxml"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(project(":aws:client:aws-client-rulesengine"))
    implementation(project(":aws:client:aws-client-s3"))

    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":codecs:xml-codec"))

    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.model)

    implementation(project(":aws:aws-credential-chain"))
    implementation(project(":aws:aws-credentials-imds"))

    implementation(project(":client:client-http-smithy"))
    implementation(project(":client:client-http-boringssl"))
}

val codegenProjections = listOf("dynamodb-client", "s3-client")

afterEvaluate {
    val projectionPaths =
        codegenProjections.map { name ->
            smithy.getPluginProjectionPath(name, "java-codegen").get()
        }
    sourceSets.named("main") {
        java {
            projectionPaths.forEach { srcDir("$it/java") }
        }
        resources {
            projectionPaths.forEach { srcDir("$it/resources") }
        }
    }
    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        // Pass original service descriptors to Shadow before processResources overwrites duplicate paths.
        projectionPaths.forEach { path ->
            from("$path/resources") {
                include("META-INF/services/**")
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    dependsOn("smithyBuild")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("smithy-java-live-benchmark-runner")
    archiveClassifier.set("")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesNotMatching(listOf("META-INF/services/**", "META-INF/smithy/manifest")) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    mergeServiceFiles()
    // Append all model manifests so runtime discovery finds every model.
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer::class.java) {
        resource = "META-INF/smithy/manifest"
    }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named("assemble") {
    dependsOn("shadowJar")
}

tasks.named("check") {
    enabled = true
}
