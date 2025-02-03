plugins {
    id("smithy-java.examples-conventions")
}

dependencies {
    api(project(":server:server-api"))
    api(project(":server:server-core"))
    implementation(project(":server:server-netty"))
    api(project(":aws:server:aws-server-restjson"))
    api(project(":core"))
    api(libs.smithy.aws.traits)
}
