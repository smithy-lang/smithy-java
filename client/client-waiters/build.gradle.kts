plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the Smithy Java Waiter implementation"

extra["displayName"] = "Smithy :: Java :: Client :: Waiters"
extra["moduleName"] = "software.amazon.smithy.java.client.waiters"

dependencies {
    api(libs.smithy.waiters)
    implementation(project(":jmespath"))
    implementation(project(":logging"))
    implementation(project(":client:client-core"))
}
