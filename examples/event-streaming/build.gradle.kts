plugins {
    id("smithy-java.examples-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    api(project(":aws:client-restjson"))
    api(libs.smithy.aws.traits)
}

tasks {
    spotbugsMain {
        enabled = false
    }

    spotbugsIt {
        enabled = false
    }
}
