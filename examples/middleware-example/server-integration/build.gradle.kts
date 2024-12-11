plugins {
    id("smithy-java.examples-conventions")
}

dependencies {
    implementation(project(":client-core"))

    api(project(":examples:middleware-example:middleware-model"))
}

// TODO: change convention so this is unnecessary
smithy {
    smithyBuildConfigs.set(project.files())
}
