plugins {
    id("smithy-java.examples-conventions")
}

dependencies {
    implementation(libs.smithy.protocol.traits)
    implementation(libs.smithy.aws.traits)
    implementation(project(":aws:integrations:lambda"))
    implementation(project(":logging"))
    implementation(project(":core"))
    implementation(project(":server-aws-rest-json1"))
    implementation(project(":server-rpcv2-cbor"))
}

tasks {
    // Generate a zip that can be uploaded to the Lambda function.
    // It will be created here: `build/distributions/lambda-endpoint-0.0.1.zip`
    register<Zip>("buildZip") {
        into("lib") {
            from(jar)
            from(configurations.runtimeClasspath)
        }
    }
}
