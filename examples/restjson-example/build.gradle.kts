plugins {
    id("smithy-java.examples-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":aws:client-json-protocols"))
    api(libs.smithy.aws.traits)
}

jmh {
    // profilers.add("async:output=flamegraph")
    // profilers.add('gc')
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
