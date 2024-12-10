plugins {
    id("smithy-java.examples-conventions")
}

dependencies {
    api(project(":server-api"))
    api(project(":server-core"))
    implementation(project(":server-netty"))
    api(project(":server-aws-rest-json1"))
    api(project(":core"))
    api(libs.smithy.aws.traits)
}
