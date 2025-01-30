plugins {
    id("smithy-java.examples-conventions")
}

dependencies {
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service:1.1.1")
    implementation(libs.smithy.protocol.traits)
    implementation(libs.smithy.aws.traits)
    implementation(project(":aws:integrations:lambda"))
    implementation(project(":logging"))
    implementation(project(":core"))
    implementation(project(":aws:server:aws-server-restjson"))
    implementation(project(":server:server-rpcv2-cbor"))
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
