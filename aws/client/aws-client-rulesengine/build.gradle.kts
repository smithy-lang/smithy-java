plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides AWS-Specific client rules engine functionality"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: Rules Engine"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.rulesengine"

dependencies {
    api(project(":aws:client:aws-client-core"))
    api(project(":client:client-rulesengine"))
    api(libs.smithy.aws.endpoints)
}
