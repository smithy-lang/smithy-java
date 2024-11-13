plugins {
    id("smithy-java.examples-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":aws:client-restjson"))
    api(project(":rpcv2-cbor-codec"))
    api(libs.smithy.aws.traits)
}

jmh {
    // profilers.add("async:output=flamegraph")
    // profilers.add('gc')
}
