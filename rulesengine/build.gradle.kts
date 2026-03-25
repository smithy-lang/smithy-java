plugins {
    id("smithy-java.module-conventions")
}

description = "Implements the rules engine traits used to resolve endpoints"

extra["displayName"] = "Smithy :: Java :: Rules Engine"
extra["moduleName"] = "software.amazon.smithy.java.rulesengine"

dependencies {
    api(project(":endpoints"))
    api(project(":context"))
    api(project(":core"))
    api(project(":jmespath"))
    api(libs.smithy.rules)
    implementation(project(":io"))
    implementation(project(":logging"))

    // Jazzer for fuzz testing
    testImplementation(libs.jazzer.junit)
    testImplementation(libs.jazzer.api)
}

tasks.test {
    maxHeapSize = "2g"
}
