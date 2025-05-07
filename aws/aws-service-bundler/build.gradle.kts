plugins {
    id("smithy-java.module-conventions")
}

description = "This module produces service bundles for AWS services"

extra["displayName"] = "Smithy :: Java :: AWS :: Service Bundler"
extra["moduleName"] = "software.amazon.smithy.java.aws.servicebundle.bundler"

dependencies {
    implementation(project(":model-bundle:model-bundle-api"))
    implementation(libs.smithy.model)
    implementation(project(":aws:aws-mcp-types"))
    // we need to be able to resolve the sigv4 and protocol traits
    implementation(libs.smithy.aws.traits)

    testImplementation(libs.aws.sdk.auth)
}
