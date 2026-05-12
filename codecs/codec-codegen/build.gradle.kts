plugins {
    id("smithy-java.module-conventions")
}

description = "Runtime code generation framework for specialized codec serializers/deserializers"

extra["displayName"] = "Smithy :: Java :: Codec Codegen"
extra["moduleName"] = "software.amazon.smithy.java.codegen.rt"

dependencies {
    api(project(":core"))
}
