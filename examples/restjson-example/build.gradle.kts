plugins {
    id("smithy-java.codegen-test-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":client-aws-rest-json1"))
    api(libs.smithy.aws.traits)
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    //profilers.add("async:output=flamegraph")
    //profilers.add('gc')
}

// TODO: eventually re-enable
// Disable spotbugs
tasks {
    spotbugsMain {
        enabled = false
    }

    spotbugsIt {
        enabled = false
    }
}
