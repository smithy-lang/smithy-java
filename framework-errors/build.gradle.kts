plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.smithy.gradle.jar)
}

description = "Smithy traits for framework errors"

extra["displayName"] = "Smithy :: Framework :: Errors"
extra["moduleName"] = "software.amazon.smithy.framework.errors"

dependencies {
    smithyBuild(project(":codegen:plugins:types"))
    api(project(":codegen:core"))
    api(project(":smithy-framework-errors"))
    api(project(":core"))
}

// Add generated Java sources to the main sourceset
afterEvaluate {
    val typesPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    sourceSets {
        main {
            java {
                srcDir(typesPath)
            }
        }
    }
}

// TODO: Is there some way to get gradle to pick this dep up automatically?
tasks.named("compileJava") {
    dependsOn("smithyBuild")
}
