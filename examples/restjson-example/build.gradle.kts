plugins {
    id("smithy-java.codegen-test-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":client-aws-rest-json1"))
    api(libs.smithy.aws.traits)
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    //profilers = ['async:output=flamegraph', 'gc']
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
