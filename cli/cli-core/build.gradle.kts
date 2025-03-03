plugins {
    id("smithy-java.module-conventions")
}

description = "This module provides the core CLI functionality"

extra["displayName"] = "Smithy :: Java :: CLI Core"
extra["moduleName"] = "software.amazon.smithy.java.cli.core"

dependencies {
    implementation(project(":client:client-core"))
    // For parsing provided args
    implementation(project(":codecs:json-codec"))
    // TODO: Ideally we would support this based on a CLI plugin or some sort of CLI-http package
    //       rather than tying the CLI to http this way.
    //
    implementation(project(":http-api"))
    // Used to handle input parsing for now.
    // TODO: Replace this with an updated, custom input parser
    implementation("com.jsoniter:jsoniter:0.9.23")
}
