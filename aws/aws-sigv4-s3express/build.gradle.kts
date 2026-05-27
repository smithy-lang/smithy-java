plugins {
    id("smithy-java.module-conventions")
}

description = "Sigv4-s3express auth scheme: bucket-scoped session credentials for S3 Express One Zone."

extra["displayName"] = "Smithy :: Java :: AWS :: SigV4 :: S3 Express"
extra["moduleName"] = "software.amazon.smithy.java.aws.sigv4.s3express"

dependencies {
    api(project(":aws:aws-sigv4"))
    api(project(":aws:aws-auth-api"))
    api(project(":aws:client:aws-client-core"))
    api(project(":auth-api"))
    api(project(":client:client-auth-api"))
    api(project(":client:client-core"))
    api(project(":core"))
    api(project(":http:http-api"))
    implementation(project(":aws:aws-config"))
    implementation(project(":logging"))
    implementation(libs.smithy.aws.traits)
}
