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

    // Client dependencies
    api(project(":aws:client-restjson"))
    api(project(":client-core"))

    // Common dependencies
    api(project(":core"))
    api(libs.smithy.aws.traits)

    // Use some common shape definitions
    implementation(project(":examples:shared-types-example"))
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
