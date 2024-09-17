plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.jmh)
}

description = "This module provides the core functionality for Smithy java"

extra["displayName"] = "Smithy :: Java :: Core"
extra["moduleName"] = "software.amazon.smithy.java.core"

dependencies {
    api(project(":common"))
    api(libs.smithy.model)
}

jmh {
    iterations = 10
    fork = 1
    //profilers = ['async:output=flamegraph', 'gc']
}

//Run all tests with a different locale to ensure we are not doing anything locale specific.
val localeTest = tasks.register<Test>("localeTest") {
    useJUnitPlatform()
    systemProperty("user.country", "DE")
    systemProperty("user.language", "de")
}

tasks.build {
    dependsOn(localeTest)
}
