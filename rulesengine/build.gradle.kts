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
    // CompactStringAccess: zero-copy access to a String's Latin1 bytes, so host-label and bucket-name
    // validation can scan eight bytes at a time instead of one char at a time.
    implementation(project(":codecs:codec-commons", configuration = "shadow"))

    // Jazzer for fuzz testing
    testImplementation(libs.jazzer.junit)
    testImplementation(libs.jazzer.api)
}

tasks.test {
    maxHeapSize = "2g"
}
