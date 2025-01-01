plugins {
    id("smithy-java.examples-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":json-codec"))
    implementation(project(":http-binding"))
}

sourceSets {
    jmh {
    }
}

jmh {
    iterations = 3
    fork = 1
}

tasks {
    spotbugsMain {
        enabled = false
    }

    spotbugsJmh {
        enabled = false
    }
}
