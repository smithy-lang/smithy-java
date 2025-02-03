plugins {
    id("smithy-java.examples-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":aws:client:aws-client-restjson"))
    api(project(":codecs:cbor-codec"))
    api(libs.smithy.aws.traits)
}

jmh {
    // profilers.add("async:output=flamegraph")
    // profilers.add('gc')
}
