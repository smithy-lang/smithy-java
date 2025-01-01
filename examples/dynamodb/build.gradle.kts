plugins {
    id("smithy-java.examples-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":aws:client-awsjson"))
    api(libs.smithy.aws.traits)
    implementation(project(":aws:sigv4"))
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    // profilers.add("async:output=flamegraph")
    // profilers.add("gc")
}

tasks {
    spotbugsMain {
        enabled = false
    }

    spotbugsJmh {
        enabled = false
    }
}
