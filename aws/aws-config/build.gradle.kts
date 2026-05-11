plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
}

description = "This module provides parsing of AWS shared config and credentials files " +
    "(~/.aws/config, ~/.aws/credentials) and an AwsCredentialsResolver backed by them."

extra["displayName"] = "Smithy :: Java :: AWS :: Config"
extra["moduleName"] = "software.amazon.smithy.java.aws.config"

dependencies {
    api(project(":aws:aws-auth-api"))
    api(project(":auth-api"))
    implementation(project(":logging"))
    implementation(project(":client:client-core"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":aws:aws-credential-chain"))
    testImplementation("tools.jackson.core:jackson-databind:3.1.2")
}
