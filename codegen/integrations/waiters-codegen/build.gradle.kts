plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the Smithy Java Waiter codegen integration"

extra["displayName"] = "Smithy :: Java :: Waiters :: Codegen"
extra["moduleName"] = "software.amazon.smithy.java.codegen.waiters"

dependencies {
    implementation(project(":client:client-waiters"))
    implementation(project(":codegen:plugins:client-codegen"))
}
