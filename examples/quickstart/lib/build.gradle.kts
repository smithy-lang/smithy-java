description = "A package for a smithy definition of a cafe service."

plugins {
    `java-library`
    // Packages the models in this package into a jar for sharing/distribution by other packages
    alias(libs.plugins.smithy.jar)
}

dependencies {
    // Adds the aws protocol traits
    api(libs.smithy.aws.traits)
}

// Helps Intellij plugin identify models
sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}
