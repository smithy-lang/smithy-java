plugins {
    id("smithy-java.codegen-test-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":client-aws-rest-json1"))
    api(project(":rpcv2-cbor-codec"))
    api(libs.smithy.aws.traits)
}

jmh {
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
