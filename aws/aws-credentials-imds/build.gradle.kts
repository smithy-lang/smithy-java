plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides an IMDS-based credential provider for EC2 instances."

extra["displayName"] = "Smithy :: Java :: AWS :: Credentials :: IMDS"
extra["moduleName"] = "software.amazon.smithy.java.aws.credentials.imds"

dependencies {
    api(project(":aws:aws-auth-api"))
    api(project(":auth-api"))
    implementation(project(":aws:aws-credential-chain"))
    implementation(project(":logging"))
    implementation(project(":codecs:json-codec"))

    testImplementation(project(":core"))
}
