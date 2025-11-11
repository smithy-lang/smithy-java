// Configure fuzz testing for codec modules using Jazzer

configure<SourceSetContainer> {
    val main by getting
    val test by getting

    // Create a separate "fuzz" source set for fuzz tests
    create("fuzz") {
        compileClasspath += main.output + configurations["testRuntimeClasspath"] + configurations["testCompileClasspath"]
        runtimeClasspath += output + compileClasspath + test.runtimeClasspath + test.output
    }
}

// Add fuzz-test-harness dependency to the fuzz source set
dependencies {
    "fuzzImplementation"(project(":fuzz-test-harness"))
}

// Create the fuzz test task
tasks.register<Test>("fuzz") {
    description = "Run fuzz tests using Jazzer"
    group = "verification"

    val fuzzSourceSet = project.the<SourceSetContainer>()["fuzz"]
    testClassesDirs = fuzzSourceSet.output.classesDirs
    classpath = fuzzSourceSet.runtimeClasspath

    useJUnitPlatform()

    // Fork for each test class to ensure isolation
    setForkEvery(1)

    // Memory settings
    maxHeapSize = "2048m"
    minHeapSize = "2048m"

    // Enable Jazzer fuzzing mode
    environment("JAZZER_FUZZ", "1")

    // Jazzer configuration
    systemProperty("jazzer.valueprofile", "1")
    systemProperty("jazzer.coverage_report", "${project.layout.buildDirectory}/reports/jazzer")

    systemProperty("jazzer.instrumentation_includes", "software.amazon.smithy.java.**")

    val corpusDir = "${project.projectDir}/src/fuzz/resources/corpus"
    if (file(corpusDir).exists()) {
        systemProperty("jazzer.internal.arg.0", corpusDir)
    }

    val maxDuration = project.findProperty("fuzz.maxDuration") ?: "1h"
    systemProperty("jazzer.max_duration", maxDuration)
}