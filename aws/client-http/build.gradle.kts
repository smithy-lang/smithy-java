plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides AWS-Specific http client functionality"

extra["displayName"] = "Smithy :: Java :: AWS :: Client-HTTP"
extra["moduleName"] = "software.amazon.smithy.java.aws.client-http"

dependencies {
    implementation(project(":auth-api"))
    // Provides sigv4 trait
    implementation(libs.smithy.aws.traits)
    implementation(project(":logging"))
    // Provides SmithyHttpRequest
    implementation(project(":http-api"))
    // Provides Datastream
    implementation(project(":core"))
    // Provides clientPlugin and associated annotations
    implementation(project(":client-core"))
}

tasks {
    test {
        // Values used to test system property identity resolver
        systemProperties["aws.accessKeyId"] = "property_access_key"
        systemProperties["aws.secretAccessKey"] = "property_secret_key"
        systemProperties["aws.sessionToken"] = "property_token"

        // Values used to test environment Identity resolver
        environment("AWS_ACCESS_KEY_ID", "env_access_key")
        environment("AWS_SECRET_ACCESS_KEY", "env_secret_key")
        environment("AWS_SESSION_TOKEN", "env_token")
    }
}
