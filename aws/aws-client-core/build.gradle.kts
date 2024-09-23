plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides AWS-Specific client functionality"

extra["displayName"] = "Smithy :: Java :: AWS :: Client-Core"
extra["moduleName"] = "software.amazon.smithy.java.aws.client-core"

dependencies {
    api(project(":client-core"))
}
