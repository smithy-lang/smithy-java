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

// Extension to allow modules to register additional protocol test runs with custom configuration.
abstract class ProtocolTestRunsExtension {
    internal val runs = mutableListOf<Pair<String, Action<Test>>>()

    fun run(name: String, action: Action<Test>) {
        runs.add(name to action)
    }
}

val protocolTestRuns = extensions.create<ProtocolTestRunsExtension>("protocolTestRuns")

afterEvaluate {
    val itSourceSet = project.the<SourceSetContainer>()["it"]
    val runs = protocolTestRuns.runs

    if (runs.isNotEmpty()) {
        tasks.named<Test>("integ") {
            enabled = false
        }
        for ((name, action) in runs) {
            val task = tasks.register<Test>("integ-$name") {
                useJUnitPlatform()
                testClassesDirs = itSourceSet.output.classesDirs
                classpath = itSourceSet.runtimeClasspath
                action.execute(this)
            }
            tasks.named("integ") { dependsOn(task) }
        }
    }

    // Ensure integ tests are executed as part of test suite
    tasks["test"].finalizedBy("integ")
}
