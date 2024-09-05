import gradle.kotlin.dsl.accessors._393fb14d292c021aa4a5c68db9b29ae3.sourceSets
import org.gradle.kotlin.dsl.project

plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.integ-test-conventions")
}

dependencies {
    testImplementation(project(":protocol-tests"))
}

// Do not run spotbugs on integration tests
tasks.named("spotbugsIt") {
    enabled = false
}

val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()

// Add generated sources to integration test sources
sourceSets {
    named("it") {
        java {
            srcDir(generatedSrcDir)
        }
    }
}
