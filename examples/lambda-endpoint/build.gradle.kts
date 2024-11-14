plugins {
    id("smithy-java.examples-conventions")
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")

    implementation(libs.smithy.protocol.traits)
    implementation(project(":aws:integrations:lambda"))
    implementation(project(":core"))
    implementation(project(":logging"))
    implementation(project(":server"))
    implementation(project(":server-rpcv2-cbor"))
    implementation(project(":server-core"))
    testImplementation(project(":rpcv2-cbor-codec"))
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
