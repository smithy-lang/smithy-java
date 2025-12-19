plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides support for Querying documents using JMESPath expressions"

extra["displayName"] = "Smithy :: Java :: JMESPath"
extra["moduleName"] = "software.amazon.smithy.java.jmespath"

dependencies {
    api(project(":core"))
    api(libs.smithy.jmespath)
    testImplementation(libs.smithy.jmespath.tests)
}
