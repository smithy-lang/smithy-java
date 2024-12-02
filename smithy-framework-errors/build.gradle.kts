/**
 * This package would be used by ALL code generators to create a "framework-errors" package
 * specific to their language.
 */

plugins {
    id("smithy-java.module-conventions")
    alias(libs.plugins.smithy.gradle.jar)
}

description = "Smithy framework errors"

extra["displayName"] = "Smithy :: Framework :: Errors"
extra["moduleName"] = "software.amazon.smithy.framework.errors"

dependencies {
    api(project(":smithy-implicit-error-trait"))
    // TODO: Do we need to find a way to extract this? I think it is OK as is b/c this is used
    //   a bit uniquely in protocol tests.
    api(libs.smithy.validation.model)
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
