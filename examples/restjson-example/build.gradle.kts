plugins {
    id("smithy-java.codegen-test-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":client-aws-rest-json1"))
    api(libs.smithy.aws.traits)
    implementation(project(":aws:client-http"))
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    //profilers = ['async:output=flamegraph', 'gc']
}

// Disable spotbugs
tasks {
    spotbugsMain {
        enabled = false
    }

    spotbugsIt {
        enabled = false
    }

    integ {
        // Ensure AWS credentials providers can find example values
        systemProperties["aws.accessKeyId"] = "property_access_key"
        systemProperties["aws.secretAccessKey"] = "property_secret_key"
    }
}
