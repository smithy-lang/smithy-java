plugins {
    id("smithy-java.module-conventions")
}

description = "This module produces service bundles for AWS services"

extra["displayName"] = "Smithy :: Java :: AWS :: Service Bundler"
extra["moduleName"] = "software.amazon.smithy.java.aws.mcp.cli.commands"

dependencies {
    implementation(project(":mcp:mcp-bundle-api"))
    implementation(libs.smithy.model)
    implementation(libs.picocli)
    annotationProcessor(libs.picocli.codegen)
    implementation(project(":aws:aws-mcp-types"))
    // we need to be able to resolve the sigv4 and protocol traits
    implementation(libs.smithy.aws.traits)
    implementation(project(":mcp:mcp-cli-api"))
    implementation(project(":aws:aws-service-bundler"))

    testImplementation(libs.aws.sdk.auth)
}

tasks.compileJava {
    options.compilerArgs.add("-Aproject=${project.name}")
}
