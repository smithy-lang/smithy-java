plugins {
    id("smithy-java.module-conventions")
}

description = "STS-based credential providers (assume-role, web identity token)."

extra["displayName"] = "Smithy :: Java :: AWS :: Credentials :: STS"
extra["moduleName"] = "software.amazon.smithy.java.aws.credentials.sts"

// Resolvable-only configuration used solely to vendor the STS model into this
// package's JAR. It is intentionally not extended into implementation/runtime, so
// the public STS model artifact never becomes a real dependency of this package.
val stsModel: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

// Extract only the STS model JSON to a private (non-discoverable) resource path.
// The model's META-INF/smithy manifest is deliberately not extracted so this package
// stays invisible to Smithy classpath discovery. StsClientFactory loads the model by
// explicit resource and relies on discoverModels only for trait definitions.
val extractStsModel by tasks.registering(Copy::class) {
    from(zipTree(stsModel.singleFile)) {
        include("META-INF/smithy/2011-06-15/sts-2011-06-15.json")
        // Relocate to the package path StsClientFactory loads it from.
        eachFile {
            relativePath = RelativePath(
                true,
                "software", "amazon", "smithy", "java", "aws", "credentials", "sts", name
            )
        }
    }
    into(layout.buildDirectory.dir("generated-resources/sts-model"))
    includeEmptyDirs = false
}

sourceSets.main {
    resources.srcDir(extractStsModel)
}

dependencies {
    implementation(project(":aws:aws-credential-chain"))
    implementation(project(":aws:aws-config"))
    implementation(project(":aws:aws-auth-api"))
    implementation(project(":aws:aws-credentials-imds"))
    implementation(project(":auth-api"))
    implementation(project(":client:client-core"))
    implementation(project(":client:dynamic-client"))
    implementation(project(":client:client-rulesengine"))
    implementation(project(":aws:client:aws-client-rulesengine"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(project(":aws:client:aws-client-awsquery"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":logging"))
    stsModel("software.amazon.api.models:sts:1.0.7") { isTransitive = false }
    testImplementation(project(":client:client-mock-plugin"))
    testImplementation(project(":http:http-api"))
}
