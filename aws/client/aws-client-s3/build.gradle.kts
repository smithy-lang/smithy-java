plugins {
    id("smithy-java.module-conventions")
}

description = "S3-specific client behavior: virtual-hosted-style bucket addressing for the dynamic and generated S3 clients."

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: S3"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.s3"

dependencies {
    api(project(":aws:client:aws-client-core"))
    api(project(":client:client-core"))
    api(project(":core"))
    api(project(":http:http-api"))
    implementation(project(":logging"))
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.aws.endpoints)

    testImplementation(project(":client:dynamic-client"))
    testImplementation(project(":aws:client:aws-client-restxml"))
}
