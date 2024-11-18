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
    implementation(project(":server"))
    implementation(project(":http-api"))
    implementation(project(":logging"))
    implementation(project(":core"))
    implementation(project(":server-core"))

    testImplementation(project(":rpcv2-cbor-codec"))
    testImplementation(project(":examples:lambda-endpoint"))
}
