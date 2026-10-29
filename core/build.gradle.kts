import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.jmh-conventions")
    `java-test-fixtures`
}

description = "This module provides the core functionality for Smithy java"

extra["displayName"] = "Smithy :: Java :: Core"
extra["moduleName"] = "software.amazon.smithy.java.core"

dependencies {
    api(project(":io"))
    api(project(":version-spi"))
    api(project(":retries-api"))
    api(libs.smithy.model)
    implementation(project(":logging"))
}

// Run all tests with a different locale to ensure we are not doing anything locale specific.
val localeTest =
    tasks.register<Test>("localeTest") {
        useJUnitPlatform()
        systemProperty("user.country", "DE")
        systemProperty("user.language", "de")
    }

tasks {
    build {
        dependsOn(localeTest)
    }

    processResources {
        // Update the Version.java class to reflect current version of project
        filter<ReplaceTokens>("tokens" to mapOf("SmithyJavaVersion" to version))
    }

    withType<Test> {
        // Add version property to test version replacement
        systemProperty("smithy.java.version", version)
    }
}

// Test fixtures are build-internal. Keep them out of the published "java" component so no
// -test-fixtures artifact ships with core.
(components["java"] as AdhocComponentWithVariants).apply {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}
