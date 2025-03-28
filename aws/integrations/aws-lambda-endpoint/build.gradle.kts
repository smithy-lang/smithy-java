plugins {
    id("smithy-java.module-conventions")
}

description = """
    This module provides a basic endpoint implementation that may be used to back an AWS Lambda request handler
    """

extra["displayName"] = "Smithy :: Java :: AWS :: Integrations :: Lambda"
extra["moduleName"] = "software.amazon.smithy.java.aws.integrations.lambda"

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation(project(":server:server-api"))
    implementation(project(":http:http-api"))
    implementation(project(":logging"))
    implementation(project(":core"))
    api(project(":server:server-core"))
    testImplementation(project(":codecs:cbor-codec"))
}
