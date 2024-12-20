plugins {
    id("smithy-java.examples-conventions")
    alias(libs.plugins.smithy.gradle.jar)
}

dependencies {
    api(project(":core"))
    // TODO: remove once the implicitErrors trait is upstreamed to prelude.
    api(project(":framework-errors"))
}

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
