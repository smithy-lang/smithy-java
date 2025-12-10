import org.gradle.kotlin.dsl.project

plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.integ-test-conventions")
}

dependencies {
    testImplementation(project(":protocol-test-harness"))
}

// Do not run spotbugs on integration tests
tasks.named("spotbugsIt") {
    enabled = false
}

// Ensure integ tests are executed as part of test suite
tasks["test"].finalizedBy("integ")
