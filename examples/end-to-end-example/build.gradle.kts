plugins {
    id("smithy-java.examples-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    // Server dependencies
    api(project(":server-api"))
    api(project(":server-core"))
    implementation(project(":server-netty"))
    api(project(":server-aws-rest-json1"))
    implementation(libs.smithy.waiters)
    // Client dependencies
    api(project(":aws:client-restjson"))
    api(project(":client-core"))

    // Common dependencies
    api(project(":core"))
    api(libs.smithy.aws.traits)

    // Use some common shape definitions
    implementation(project(":examples:shared-types-example"))

    // Example middleware for client
    implementation(project(":examples:middleware-example:client-integration"))

    // TODO: Add example server middleware once applicable
    smithyBuild(project(":codegen:integrations:waiters-codegen"))
    implementation(project(":waiters"))
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    // profilers.add("async:output=flamegraph")
    // profilers.add('gc')
}

// Disable spotbugs
tasks {
    spotbugsMain {
        enabled = false
    }

    spotbugsIt {
        enabled = false
    }
}
