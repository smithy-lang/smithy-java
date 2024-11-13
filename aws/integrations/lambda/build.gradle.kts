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
    implementation(project(":server-aws-rest-json1"))
    implementation(project(":json-codec"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(project(":examples:server-example"))
}