plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.smithy.gradle.jar)
}

description = "Smithy traits for framework errors"

extra["displayName"] = "Smithy :: Framework :: Errors"
extra["moduleName"] = "software.amazon.smithy.framework.errors"

dependencies {
    smithyBuild(project(":codegen:plugins:types"))
    api(project(":core"))

    // Validation error is imported separately, b/c it is used a bit uniquely in protocol tests.
    // TODO: Can this be collapsed into the framework errors when they are upstreamed?
    api(libs.smithy.validation.model)
}

// Add generated Java sources to the main sourceSet
afterEvaluate {
    val typesPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    sourceSets {
        main {
            java {
                srcDir(typesPath)
            }
            resources {
                srcDir(typesPath)
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}
