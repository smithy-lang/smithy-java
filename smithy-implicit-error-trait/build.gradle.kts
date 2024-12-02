/**
 * TODO: The trait in this package would be upstreamed to smithy-model prelude.
 */

plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.smithy.gradle.jar)
}

description = "Smithy traits for framework errors"

extra["displayName"] = "Smithy :: Implicit :: Errors :: Traits"
extra["moduleName"] = "software.amazon.smithy.framework.errors.traits"

dependencies {
    implementation(libs.smithy.model)
}

// Helps Intellij plugin identify models
sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}

smithy {
    smithyBuildConfigs.set(project.files())
}
