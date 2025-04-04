plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.smithy.gradle.jar)
}

description = "Smithy framework errors for Smithy Java"

extra["displayName"] = "Smithy :: Framework Errors"
extra["moduleName"] = "software.amazon.smithy.framework"

dependencies {
    smithyBuild(project(":codegen:plugins:types-codegen"))
    api(project(":core"))

    // Validation error is imported separately, b/c it is used a bit uniquely in protocol tests.
    // TODO: Can this be collapsed into the framework errors when they are upstreamed?
    api(libs.smithy.validation.model) {
        exclude(group = "software.amazon.smithy", module = "smithy-cli")
    }
}

// Add generated Java sources to the main sourceSet
afterEvaluate {
    val typesPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    sourceSets {
        main {
            java {
                srcDir(typesPath)
                include("software/**")
            }
            resources {
                srcDir(typesPath)
                include("META-INF/**")
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

// Needed because sources-jar needs to run after smithy-build is done
tasks.named("sourcesJar") {
    mustRunAfter("compileJava")
}

// Helps Intellij plugin identify models
sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}
