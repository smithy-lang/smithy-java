plugins {
    id("smithy-java.examples-conventions")
    // Package smithy models alongside jar for downstream
    alias(libs.plugins.smithy.gradle.jar)
}

dependencies {
    api(project(":core"))
}
