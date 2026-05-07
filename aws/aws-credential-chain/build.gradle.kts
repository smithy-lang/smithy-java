plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the AWS credential provider chain with SPI-based provider discovery."

extra["displayName"] = "Smithy :: Java :: AWS :: Credential Chain"
extra["moduleName"] = "software.amazon.smithy.java.aws.credentials.chain"

dependencies {
    api(project(":aws:aws-auth-api"))
    api(project(":auth-api"))
    implementation(project(":logging"))
}
