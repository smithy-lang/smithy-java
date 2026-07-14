plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.fuzz-test")
}

description = "This module provides parsing of AWS shared config and credentials files " +
    "(~/.aws/config, ~/.aws/credentials) and the profile data model."

extra["displayName"] = "Smithy :: Java :: AWS :: Config"
extra["moduleName"] = "software.amazon.smithy.java.aws.config"

dependencies {
    api(project(":aws:aws-auth-api"))
    implementation(project(":logging"))
    testImplementation("tools.jackson.core:jackson-databind:3.2.1")
}
