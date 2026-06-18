plugins {
    id("smithy-java.module-conventions")
}

description = "STS-based credential providers (assume-role, web identity token)."

extra["displayName"] = "Smithy :: Java :: AWS :: Credentials :: STS"
extra["moduleName"] = "software.amazon.smithy.java.aws.credentials.sts"

dependencies {
    implementation(project(":aws:aws-credential-chain"))
    implementation(project(":aws:aws-config"))
    implementation(project(":aws:aws-auth-api"))
    implementation(project(":aws:aws-credentials-imds"))
    implementation(project(":auth-api"))
    implementation(project(":client:client-core"))
    implementation(project(":client:dynamic-client"))
    implementation(project(":client:client-rulesengine"))
    implementation(project(":aws:client:aws-client-rulesengine"))
    implementation(project(":aws:client:aws-client-awsjson"))
    implementation(project(":aws:client:aws-client-awsquery"))
    implementation(project(":codecs:json-codec", configuration = "shadow"))
    implementation(project(":logging"))
    implementation("software.amazon.api.models:sts:1.0.7")
    testImplementation(project(":client:client-mock-plugin"))
    testImplementation(project(":http:http-api"))
}
